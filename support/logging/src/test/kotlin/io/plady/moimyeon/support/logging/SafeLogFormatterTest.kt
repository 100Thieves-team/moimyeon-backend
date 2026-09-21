package io.plady.moimyeon.support.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.event.KeyValuePair
import org.springframework.boot.json.JsonParserFactory
import org.springframework.mock.env.MockEnvironment

class SafeLogFormatterTest {
    @Test
    fun `예외 체인은 설정된 깊이와 프레임 수로 제한하고 앱 예외의 타입과 메시지는 남긴다`() {
        val sanitizer = LogSanitizer(LoggingProperties(maxStackFrames = 1, maxExceptionDepth = 1), "core-api", "dev", "test-release")
        val event = event().apply {
            setThrowableProxy(ThrowableProxy(ProbeException("outer failed", IllegalArgumentException("inner failed"))))
        }

        val fields = sanitizer.sanitize(event)
        val exceptions = fields["exceptions"] as List<*>
        assertThat(exceptions).hasSize(1)
        val outer = exceptions.single() as Map<*, *>
        assertThat(outer["type"]).isEqualTo("io.plady.moimyeon.support.logging.ProbeException")
        assertThat(outer["message"]).isEqualTo("outer failed")
        assertThat(outer["frames"] as List<*>).hasSize(1)
        assertThat(fields.toString()).doesNotContain("inner failed")
    }

    private val formatter = SafeLogFormatter(
        MockEnvironment()
            .withProperty("moimyeon.logging.environment", "dev")
            .withProperty("spring.application.name", "core-api")
            .withProperty("APP_RELEASE", "test-release"),
    )

    @Test
    fun `메시지와 인자를 포맷해 보존하고 미등록 사건은 application log로 표시한다`() {
        val event = event(message = "room.create roomId={} hostId={}").apply {
            argumentArray = arrayOf("3f2a", "9c1e")
        }

        val fields = json(formatter.format(event))

        assertThat(fields).containsEntry("eventCode", "application.log")
            .containsEntry("message", "room.create roomId=3f2a hostId=9c1e")
            .containsEntry("logger", "io.plady.moimyeon.Test")
            .containsKey("thread")
    }

    @Test
    fun `key value와 MDC를 필드로 보존하되 예약 필드는 덮어쓰지 않는다`() {
        val event = event(
            mdc = mapOf("traceId" to "0123456789abcdef0123456789abcdef", "memberId" to "9c1e", "level" to "forged"),
        ).apply {
            setKeyValuePairs(listOf(KeyValuePair("roomId", "3f2a"), KeyValuePair("eventCode", "forged")))
        }

        val fields = json(formatter.format(event))

        assertThat(fields).containsEntry("traceId", "0123456789abcdef0123456789abcdef")
            .containsEntry("memberId", "9c1e")
            .containsEntry("roomId", "3f2a")
            .containsEntry("level", "INFO")
            .containsEntry("eventCode", "service.ready")
    }

    @Test
    fun `라우터가 읽는 예약 필드는 MDC나 key value로 주입할 수 없다`() {
        val event = event(
            mdc = mapOf("category" to "growth", "status" to "200", "route" to "/forged", "requestId" to "not-a-uuid", "traceId" to "0".repeat(32)),
        ).apply {
            setKeyValuePairs(listOf(KeyValuePair("durationMs", 1), KeyValuePair("errorCode", "E999"), KeyValuePair("impact", "high")))
        }

        val fields = json(formatter.format(event))

        assertThat(fields).doesNotContainKeys("category", "status", "route", "requestId", "traceId", "spanId", "durationMs", "errorCode", "impact")
    }

    @Test
    fun `trace와 span은 형식이 맞고 0이 아닐 때만 남긴다`() {
        val valid = event(mdc = mapOf("traceId" to "0123456789abcdef0123456789abcdef", "spanId" to "0123456789abcdef"))
        val invalid = event(mdc = mapOf("traceId" to "forged\ntrace", "spanId" to "0123456789abcdef"))

        assertThat(json(formatter.format(valid))).containsEntry("traceId", "0123456789abcdef0123456789abcdef").containsEntry("spanId", "0123456789abcdef")
        assertThat(json(formatter.format(invalid))).doesNotContainKeys("traceId", "spanId")
    }

    @Test
    fun `앱 밖 예외의 메시지는 사용자 데이터를 되풀이할 수 있어 타입만 남긴다`() {
        val event = event(level = Level.ERROR).apply {
            setThrowableProxy(ThrowableProxy(ProbeException("resume.register failed", IllegalStateException("Duplicate entry 'private-nickname'"))))
        }

        val output = formatter.format(event)
        val exceptions = json(output)["exceptions"] as List<*>

        val inner = exceptions[1] as Map<*, *>
        assertThat((exceptions[0] as Map<*, *>)["message"]).isEqualTo("resume.register failed")
        assertThat(inner["type"]).isEqualTo("java.lang.IllegalStateException")
        assertThat(inner.keys).doesNotContain("message")
        assertThat(output).doesNotContain("private-nickname")
    }

    @Test
    fun `요청 처리 중 발생한 일반 오류 로그에도 서버 요청 ID를 보존한다`() {
        val requestId = "00000000-0000-0000-0000-000000000011"
        val record = event(message = "room.create failed", mdc = mapOf("requestId" to requestId), level = Level.ERROR)

        assertThat(json(formatter.format(record))).containsEntry("requestId", requestId).containsEntry("eventCode", "application.error")
    }

    @Test
    fun `자격 증명 형태는 메시지 MDC 예외 메시지 어디에 있든 가린다`() {
        // 시크릿 게이트가 JWT 리터럴을 막으므로 실행 시점에 형태만 조립한다.
        val jwt = listOf("eyJ" + "a".repeat(20), "b".repeat(24), "c".repeat(16)).joinToString(".")
        val event = event(message = "session.authenticate token=$jwt header=Bearer abc.def-ghi", mdc = mapOf("authorization" to "Bearer $jwt")).apply {
            setThrowableProxy(ThrowableProxy(ProbeException("rejected $jwt")))
        }

        val output = formatter.format(event)

        assertThat(output).doesNotContain(jwt).doesNotContain("abc.def-ghi")
        assertThat(json(output)).containsEntry("message", "session.authenticate token=[MASKED_JWT] header=Bearer [MASKED]")
            .containsEntry("authorization", "Bearer [MASKED]")
        assertThat(output).contains("rejected [MASKED_JWT]")
    }

    @Test
    fun `예외 체인과 긴 코드 위치가 있어도 출력은 유효한 JSON과 제한된 크기를 유지한다`() {
        var error: Throwable = IllegalStateException("root failed")
        repeat(10) {
            error = IllegalArgumentException("wrapped failed", error).apply {
                stackTrace = Array(100) { StackTraceElement("a".repeat(128), "m".repeat(128), "f".repeat(125) + ".kt", 12) }
            }
        }
        val event = event(message = "x".repeat(10_000)).apply { setThrowableProxy(ThrowableProxy(error)) }

        val output = formatter.format(event)

        assertThat(json(output)).containsKey("exceptions")
        assertThat((json(output)["message"] as String).length).isLessThanOrEqualTo(4097)
        assertThat(output.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(32 * 1024)
    }

    @Test
    fun `텍스트 형식은 메시지 뒤에 컨텍스트와 예외를 사람이 읽는 형태로 붙인다`() {
        val event = event(message = "room.create roomId=3f2a", mdc = mapOf("requestId" to "00000000-0000-0000-0000-000000000011")).apply {
            setThrowableProxy(ThrowableProxy(ProbeException("boom", IllegalStateException("Duplicate entry 'nickname'"))))
        }

        val output = formatter.formatText(event)

        assertThat(output).startsWith("2023-11-14T22:13:20Z INFO [core-api/dev]").doesNotContain("application.log")
        assertThat(output).contains(" io.plady.moimyeon.Test - room.create roomId=3f2a | requestId=00000000-0000-0000-0000-000000000011")
        assertThat(output).contains("\n  ! io.plady.moimyeon.support.logging.ProbeException: boom\n    at ")
        assertThat(output).contains("\n  ! java.lang.IllegalStateException\n").doesNotContain("Duplicate entry")
    }

    @Test
    fun `이벤트를 읽다가 실패해도 출력은 유효한 JSON을 유지한다`() {
        val event = mockk<ILoggingEvent>()
        every { event.timeStamp } throws IllegalStateException("broken event")

        val output = formatter.format(event)

        assertThat(json(output)).containsEntry("eventCode", "logging.serialization_failed")
        assertThat(output).doesNotContain("broken event")
    }

    private fun event(message: String = "service.ready", mdc: Map<String, String> = emptyMap(), level: Level = Level.INFO): LoggingEvent = LoggingEvent().apply {
        timeStamp = 1_700_000_000_000
        this.level = level
        loggerName = "io.plady.moimyeon.Test"
        threadName = "test-thread"
        this.message = message
        mdcPropertyMap = mdc
    }

    private fun json(output: String): Map<String, Any> = JsonParserFactory.getJsonParser().parseMap(output)
}
