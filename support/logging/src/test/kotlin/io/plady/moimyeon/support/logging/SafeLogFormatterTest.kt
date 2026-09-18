package io.plady.moimyeon.support.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.json.JsonParserFactory
import org.springframework.mock.env.MockEnvironment

class SafeLogFormatterTest {
    @Test
    fun `정제 규칙은 Spring 없이 설정된 예외 깊이와 프레임 수를 적용한다`() {
        val sanitizer = LogSanitizer(LoggingProperties(maxStackFrames = 1, maxExceptionDepth = 1), "core-api", "dev", "test-release")
        val event = event().apply {
            setThrowableProxy(ThrowableProxy(IllegalStateException("private@example.invalid", IllegalArgumentException("private@example.invalid"))))
        }

        val fields = sanitizer.sanitize(event)
        val exceptions = fields["exceptions"] as List<*>
        assertThat(exceptions).hasSize(1)
        assertThat((exceptions.single() as Map<*, *>)["frames"] as List<*>).hasSize(1)
        assertThat(fields.toString()).doesNotContain("private@example.invalid")
    }

    private val formatter = SafeLogFormatter(
        MockEnvironment()
            .withProperty("moimyeon.logging.environment", "dev")
            .withProperty("spring.application.name", "core-api")
            .withProperty("APP_RELEASE", "test-release"),
    )

    @Test
    fun `허용된 사건명만 남기고 임의 메시지와 인자는 포맷하지 않는다`() {
        val event = event(message = "private {}").apply {
            argumentArray = arrayOf(object {
                override fun toString(): String = error("Must not format an untrusted argument")
            })
        }

        val output = formatter.format(event)

        assertThat(json(output)).containsEntry("eventCode", "application.log")
        assertThat(output).doesNotContain("private", "Must not format")
    }

    @Test
    fun `잘못된 추적 식별자와 임의 MDC는 버린다`() {
        val event = event(
            mdc = mapOf("traceId" to "private@example.invalid\nforged", "spanId" to "0123456789abcdef", "token" to "private"),
        )

        assertThat(json(formatter.format(event))).doesNotContainKeys("traceId", "spanId", "token")
    }

    @Test
    fun `예외 체인과 긴 코드 위치가 있어도 출력은 유효한 JSON과 제한된 크기를 유지한다`() {
        var error: Throwable = IllegalStateException("private@example.invalid")
        repeat(10) {
            error = IllegalArgumentException("private@example.invalid", error).apply {
                stackTrace = Array(100) { StackTraceElement("a".repeat(128), "m".repeat(128), "f".repeat(125) + ".kt", 12) }
            }
        }
        val event = event().apply { setThrowableProxy(ThrowableProxy(error)) }

        val output = formatter.format(event)

        assertThat(json(output)).containsKey("exceptions")
        assertThat(output.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(16 * 1024)
        assertThat(output).doesNotContain("private@example.invalid")
    }

    @Test
    fun `이벤트를 읽다가 실패해도 원문을 출력하지 않는다`() {
        val event = mockk<ILoggingEvent>()
        every { event.timeStamp } throws IllegalStateException("private@example.invalid")

        val output = formatter.format(event)

        assertThat(json(output)).containsEntry("eventCode", "logging.serialization_failed")
        assertThat(output).doesNotContain("private@example.invalid")
    }

    private fun event(message: String = "service.ready", mdc: Map<String, String> = emptyMap()): LoggingEvent = LoggingEvent().apply {
        timeStamp = 1_700_000_000_000
        level = Level.INFO
        loggerName = "io.plady.moimyeon.Test"
        this.message = message
        mdcPropertyMap = mdc
    }

    private fun json(output: String): Map<String, Any> = JsonParserFactory.getJsonParser().parseMap(output)
}
