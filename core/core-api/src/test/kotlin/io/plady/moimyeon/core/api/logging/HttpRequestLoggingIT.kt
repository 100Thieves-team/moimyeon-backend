package io.plady.moimyeon.core.api.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.security.auth.JwtTokenProvider
import io.plady.moimyeon.support.logging.RequestLogEntry
import io.plady.moimyeon.support.logging.RequestLogWriter
import io.plady.moimyeon.support.logging.SafeLogFormatter
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.json.JsonParserFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(HttpRequestLoggingIT.Probes::class)
@ResourceLock("logback")
class HttpRequestLoggingIT(
    private val server: ServletWebServerApplicationContext,
    private val tokens: JwtTokenProvider,
) : ContextTest() {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    private val logger = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(RequestLogWriter::class.java)
    private var previousLevel: Level? = null
    private val records = LinkedBlockingQueue<RequestLogEntry>()
    private val formattedRecords = ConcurrentHashMap<String, Map<String, Any>>()
    private val appender = object : AppenderBase<ILoggingEvent>() {
        override fun append(event: ILoggingEvent) {
            (event.keyValuePairs?.singleOrNull { it.key == "request" }?.value as? RequestLogEntry)?.let {
                formattedRecords[requireNotNull(it.requestId)] = JsonParserFactory.getJsonParser().parseMap(SafeLogFormatter(server.environment).format(event))
                records.add(it)
            }
        }
    }

    @BeforeEach
    fun capture() {
        previousLevel = logger.level
        logger.level = Level.INFO
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun release() {
        logger.detachAppender(appender)
        logger.level = previousLevel
        appender.stop()
    }

    @Test
    fun `원문 경로와 query body 대신 등록 경로를 남기고 응답 본문을 보존한다`() {
        val body = "private@example.invalid"
        val response = send("/v1/logging-probe/echo/private-person?token=private", body = body)
        val record = nextRecord()

        assertThat(response.statusCode()).isEqualTo(201)
        assertThat(response.body()).isEqualTo(body)
        assertThat(record.routeTemplate).isEqualTo("/v1/logging-probe/echo/{id}")
        assertThat(record.status).isEqualTo(201)
        assertThat(record.requestId).isEqualTo(response.headers().firstValue("X-Probe-RequestId").orElseThrow())
        assertThat(record.toString()).doesNotContain(body, "private-person", "token=")
        assertThat(records.poll(100, TimeUnit.MILLISECONDS)).isNull()
    }

    @Test
    fun `실제 Security의 401과 403도 응답 오류 코드와 함께 기록한다`() {
        assertThat(send("/v1/logging-probe/ok", authenticated = false).statusCode()).isEqualTo(401)
        assertThat(nextRecord()).satisfies({
            assertThat(it.status).isEqualTo(401)
            assertThat(it.errorCode).isEqualTo("E1102")
            assertThat(it.routeTemplate).isEqualTo("UNMATCHED")
        })
        assertThat(send("/admin/logging-probe").statusCode()).isEqualTo(403)
        assertThat(nextRecord()).satisfies({
            assertThat(it.status).isEqualTo(403)
            assertThat(it.errorCode).isEqualTo("E1103")
        })
    }

    @Test
    fun `실제 OAuth 리다이렉트는 query 원문 없이 정해진 경로 분류로 기록한다`() {
        assertThat(send("/oauth2/authorization/google", authenticated = false).statusCode()).isEqualTo(302)
        assertThat(nextRecord().routeTemplate).isEqualTo("/oauth2/authorization/{registrationId}")
        assertThat(send("/login/oauth2/code/google?error=access_denied&error_description=private", authenticated = false).statusCode()).isEqualTo(302)
        val callback = nextRecord()
        assertThat(callback.routeTemplate).isEqualTo("/login/oauth2/code/{registrationId}")
        assertThat(callback.toString()).doesNotContain("private", "access_denied")
    }

    @Test
    fun `Advice의 400 404 500 응답에 있는 오류 코드를 기록한다`() {
        for ((path, status, code) in listOf(Triple("/bad", 400, "E400"), Triple("/missing", 404, "E1006"), Triple("/failure", 500, "E500"))) {
            assertThat(send("/v1/logging-probe$path").statusCode()).isEqualTo(status)
            val record = nextRecord()
            assertThat(record.status).isEqualTo(status)
            assertThat(record.errorCode).isEqualTo(code)
        }
    }

    @Test
    fun `비동기 완료와 예외를 최종 상태로 한 번 기록한다`() {
        for ((path, status) in listOf("/async" to 202, "/async-failure" to 500)) {
            assertThat(send("/v1/logging-probe$path").statusCode()).isEqualTo(status)
            val record = nextRecord()
            assertThat(record.status).isEqualTo(status)
            assertThat(record.routeTemplate).isEqualTo("/v1/logging-probe$path")
            assertThat(record.durationMs).isGreaterThanOrEqualTo(0)
            assertThat(records.poll(100, TimeUnit.MILLISECONDS)).isNull()
        }
    }

    @Test
    fun `직접 비동기 완료와 타임아웃도 최종 상태로 한 번 기록한다`() {
        for ((path, status) in listOf("/async-direct" to 202, "/async-timeout" to 503)) {
            val response = send("/v1/logging-probe$path")
            assertThat(response.statusCode()).isEqualTo(status)
            val record = nextRecord()
            assertThat(record.status).isEqualTo(status)
            assertThat(record.routeTemplate).isEqualTo("/v1/logging-probe$path")
            assertThat(records.poll(100, TimeUnit.MILLISECONDS)).isNull()
        }
    }

    @Test
    fun `비동기 처리가 다른 핸들러로 dispatch돼도 최초 요청 경로를 보존한다`() {
        assertThat(send("/v1/logging-probe/async-dispatch").statusCode()).isEqualTo(200)
        assertThat(nextRecord().routeTemplate).isEqualTo("/v1/logging-probe/async-dispatch")
        assertThat(records.poll(100, TimeUnit.MILLISECONDS)).isNull()
    }

    @Test
    fun `확장 HTTP 메서드도 로그를 잃지 않고 실제 응답 상태를 기록한다`() {
        val response = send("/v1/logging-probe/ok", method = "BREW")
        val record = nextRecord()
        assertThat(response.statusCode()).isGreaterThanOrEqualTo(400)
        assertThat(record.status).isEqualTo(response.statusCode())
        assertThat(record.method).isEqualTo("UNKNOWN")
    }

    @Test
    fun `미매칭과 sendError의 완료 로그는 오류 페이지 경로로 덮이지 않는다`() {
        val unknown = send("/not-a-registered-route/private-person")
        val unknownLog = nextRecord()
        assertThat(unknownLog.status).isEqualTo(unknown.statusCode())
        assertThat(unknownLog.routeTemplate).isEqualTo("UNMATCHED")
        val error = send("/v1/logging-probe/send-error")
        val errorLog = nextRecord()
        assertThat(errorLog.status).isEqualTo(error.statusCode())
        assertThat(errorLog.routeTemplate).isEqualTo("/v1/logging-probe/send-error")
        assertThat(records.poll(100, TimeUnit.MILLISECONDS)).isNull()
    }

    @Test
    fun `연속 요청은 서로 다른 서버 요청 ID를 가진다`() {
        send("/v1/logging-probe/ok")
        val first = nextRecord()
        send("/v1/logging-probe/ok")
        val second = nextRecord()
        assertThat(first.requestId).isNotBlank().isNotEqualTo(second.requestId)
    }

    @Test
    fun `sampling이 0인 실제 관측 context를 요청 처리와 완료 로그에서 공유한다`() {
        assertThat(server.environment.getProperty("management.tracing.sampling.probability", Double::class.java)).isZero()
        val response = send("/v1/logging-probe/context")
        val record = nextRecord()
        val traceId = response.headers().firstValue("X-Probe-TraceId").orElseThrow()
        val spanId = response.headers().firstValue("X-Probe-SpanId").orElseThrow()
        val httpSpanId = response.headers().firstValue("X-Probe-Http-SpanId").orElseThrow()
        assertThat(traceId).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32))
        assertThat(spanId).matches("[0-9a-f]{16}").isNotEqualTo("0".repeat(16))
        assertThat(httpSpanId).matches("[0-9a-f]{16}").isNotEqualTo("0".repeat(16))
        // Security may open child spans inside the HTTP span; the trace is shared, not every span ID.
        assertThat(formattedRecords[record.requestId]).containsEntry("traceId", traceId).containsEntry("spanId", httpSpanId)
    }

    private fun nextRecord(): RequestLogEntry = requireNotNull(records.poll(5, TimeUnit.SECONDS)) { "Request completion log was not emitted" }

    private fun send(path: String, authenticated: Boolean = true, body: String? = null, method: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${requireNotNull(server.webServer).port}$path"))
            .timeout(Duration.ofSeconds(10))
        if (authenticated) builder.header("Authorization", "Bearer ${tokens.issueWithoutExpiration(MEMBER_ID, MemberRole.USER)}")
        if (body != null) builder.header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString(body)) else builder.GET()
        method?.let { builder.method(it, HttpRequest.BodyPublishers.noBody()) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Probes {
        @Bean
        fun loggingProbeController() = ProbeController()

        @Bean
        fun unmappedProbe(): FilterRegistrationBean<Filter> = FilterRegistrationBean<Filter>(
            Filter { request, response, chain ->
                request.setAttribute("probe.http-span", MDC.get("spanId"))
                if ((request as HttpServletRequest).requestURI.startsWith("/not-a-registered-route/")) {
                    (response as HttpServletResponse).sendError(404)
                } else {
                    chain.doFilter(request, response)
                }
            },
        ).apply { order = Ordered.HIGHEST_PRECEDENCE + 3 }
    }

    @RestController
    class ProbeController {
        @GetMapping("/v1/logging-probe/ok")
        fun ok(): String = "ok"

        @GetMapping("/v1/logging-probe/context")
        fun context(request: HttpServletRequest): ResponseEntity<String> = ResponseEntity.ok()
            .header("X-Probe-TraceId", MDC.get("traceId") ?: "none")
            .header("X-Probe-SpanId", MDC.get("spanId") ?: "none")
            .header("X-Probe-Http-SpanId", request.getAttribute("probe.http-span") as? String ?: "none")
            .body("context")

        @PostMapping("/v1/logging-probe/echo/{id}")
        fun echo(@RequestBody body: String): ResponseEntity<String> = ResponseEntity.status(201).header("X-Probe-RequestId", MDC.get("requestId")).body(body)

        @GetMapping("/v1/logging-probe/bad")
        fun bad(): String = throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)

        @GetMapping("/v1/logging-probe/missing")
        fun missing(): String = throw CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        @GetMapping("/v1/logging-probe/failure")
        fun failure(): String = throw IllegalStateException("private@example.invalid")

        @GetMapping("/v1/logging-probe/async")
        fun async(): Callable<ResponseEntity<String>> = Callable { ResponseEntity.status(202).body("async") }

        @GetMapping("/v1/logging-probe/async-failure")
        fun asyncFailure(): Callable<String> = Callable { throw IllegalStateException("private@example.invalid") }

        @GetMapping("/v1/logging-probe/async-direct")
        fun asyncDirect(request: HttpServletRequest, response: HttpServletResponse) {
            val async = request.startAsync()
            response.status = 202
            response.writer.write("async-direct")
            async.complete()
        }

        @GetMapping("/v1/logging-probe/async-timeout")
        fun asyncTimeout(): DeferredResult<ResponseEntity<String>> = DeferredResult<ResponseEntity<String>>(50L).apply {
            onTimeout { setErrorResult(ResponseEntity.status(503).body("timeout")) }
        }

        @GetMapping("/v1/logging-probe/async-dispatch")
        fun asyncDispatch(request: HttpServletRequest) {
            val async = request.startAsync()
            async.dispatch("/v1/logging-probe/ok")
        }

        @GetMapping("/v1/logging-probe/send-error")
        fun sendError(response: HttpServletResponse) = response.sendError(409)
    }

    companion object {
        private val MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011")
    }
}
