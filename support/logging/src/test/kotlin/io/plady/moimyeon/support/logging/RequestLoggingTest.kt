package io.plady.moimyeon.support.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.slf4j.LoggerFactory
import org.springframework.boot.json.JsonParserFactory
import org.springframework.mock.env.MockEnvironment
import java.time.Duration

@ResourceLock("logback")
class RequestLoggingTest {
    @Test
    fun `요청 요약을 kotlin logging으로 기록하면 고정 필드를 JSON에 보존한다`() {
        capture { writer, events ->
            writer.write(RequestLogEntry("POST", "/v1/rooms/{roomId}", 201, 42))

            val fields = json(events.single())
            assertThat(fields).containsEntry("eventCode", "http.request.completed")
                .containsEntry("method", "POST")
                .containsEntry("route", "/v1/rooms/{roomId}")
            assertThat((fields["status"] as Number).toInt()).isEqualTo(201)
            assertThat((fields["durationMs"] as Number).toLong()).isEqualTo(42)
            assertThat(fields).doesNotContainKeys("body", "headers", "clientIp")
        }
    }

    @Test
    fun `slow 설정 경계부터 느린 성공을 WARN으로 기록하고 실패 요약은 INFO로 남긴다`() {
        capture(LoggingProperties(slowRequestThreshold = Duration.ofMillis(250))) { writer, events ->
            writer.write(RequestLogEntry("GET", "/v1/rooms", 200, 249))
            writer.write(RequestLogEntry("GET", "/v1/rooms", 200, 250))
            writer.write(RequestLogEntry("GET", "/v1/rooms", 500, 300, "E500"))

            assertThat(events.map { it.level }).containsExactly(Level.INFO, Level.WARN, Level.INFO)
            assertThat(json(events[1])).containsEntry("eventCode", "http.request.slow")
            assertThat(json(events[2])).containsEntry("errorCode", "E500")
            assertThat((json(events[2])["status"] as Number).toInt()).isEqualTo(500)
        }
    }

    @Test
    fun `제외 경로는 경계까지 비교하고 health 실패는 남긴다`() {
        capture(LoggingProperties(excludedPaths = listOf("/health"))) { writer, events ->
            writer.write(RequestLogEntry("GET", "/health", 200, 1))
            writer.write(RequestLogEntry("GET", "/health/readiness", 200, 1))
            writer.write(RequestLogEntry("GET", "/healthcare", 200, 1))
            writer.write(RequestLogEntry("GET", "/health", 503, 1))

            assertThat(events.map { json(it)["route"] }).containsExactly("/healthcare", "/health")
        }
    }

    @Test
    fun `본문이 섞인 경로나 잘못된 상태는 로그 객체로 만들지 않는다`() {
        for (route in listOf("/v1/rooms?token=private", "/v1/members/private@example.invalid", "/v1/rooms\nforged")) {
            assertThatThrownBy { RequestLogEntry("GET", route, 200, 1) }.isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThatThrownBy { RequestLogEntry("GET", "/v1/rooms", 999, 1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { RequestLogEntry("GET", "/v1/rooms", 200, -1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `요청 사건명을 흉내 낸 임의 payload는 요청 요약으로 승격하지 않는다`() {
        capture { _, events ->
            KotlinLogging.logger(LOGGER_NAME).atInfo {
                message = "http.request.completed"
                payload = mapOf("request" to mapOf("method" to "GET", "body" to "private@example.invalid"))
            }

            val fields = json(events.single())
            assertThat(fields).containsEntry("eventCode", "application.log").doesNotContainKeys("method", "body")
        }
    }

    private fun capture(properties: LoggingProperties = LoggingProperties(), block: (RequestLogWriter, List<ILoggingEvent>) -> Unit) {
        val logger = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(LOGGER_NAME)
        val oldLevel = logger.level
        val oldAdditive = logger.isAdditive
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.INFO
        logger.isAdditive = false
        logger.addAppender(appender)
        try {
            block(RequestLogWriter(properties, KotlinLogging.logger(LOGGER_NAME)), appender.list)
        } finally {
            logger.detachAppender(appender)
            logger.level = oldLevel
            logger.isAdditive = oldAdditive
            appender.stop()
        }
    }

    private fun json(event: ILoggingEvent): Map<String, Any> = JsonParserFactory.getJsonParser().parseMap(
        SafeLogFormatter(MockEnvironment().withProperty("moimyeon.logging.environment", "test")).format(event),
    )

    companion object {
        private const val LOGGER_NAME = "io.plady.moimyeon.request.logging.test"
    }
}
