package io.plady.moimyeon.support.logging

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEventAttributeValue
import io.sentry.SentryLogLevel
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import io.sentry.protocol.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SentryPrivacyFilterTest {
    private val filter = SentryPrivacyFilter("core-api", "dev", "test-release")

    @Test
    fun `오류에서 요청 사용자 임의 메시지와 MDC를 제거하고 코드 위치만 남긴다`() {
        val source = SentryEvent().apply {
            request = Request().apply {
                url = "https://api.example/v1/user/secret-user"
                queryString = "token=secret"
                headers = mapOf("Authorization" to "Bearer secret")
                data = mapOf("payload" to "private text")
            }
            user = User().apply { email = "private@example.com" }
            message = Message().apply { formatted = "private@example.com secret" }
            setExtra("payload", "private text")
            setTag("user", "private-user")
            contexts["payload"] = "private text"
            exceptions = listOf(
                SentryException().apply {
                    type = "IllegalStateException"
                    value = "request payload contains private@example.com"
                    stacktrace = SentryStackTrace().apply {
                        frames = listOf(
                            SentryStackFrame().apply {
                                module = "io.plady.moimyeon.Worker"
                                function = "process"
                                filename = "Worker.kt"
                                lineno = 42
                                vars = mapOf("token" to "secret")
                                contextLine = "private source"
                                absPath = "/private/path/Worker.kt"
                            },
                        )
                    }
                },
            )
        }

        val result = filter.event(source)

        assertThat(result.eventId).isEqualTo(source.eventId)
        assertThat(result.request).isNull()
        assertThat(result.user).isNull()
        assertThat(result.extras).isNullOrEmpty()
        assertThat(result.contexts["payload"]).isNull()
        assertThat(result.tags).containsOnlyKeys("service.name")
        assertThat(result.release).isEqualTo("test-release")
        assertThat(result.environment).isEqualTo("dev")
        assertThat(result.exceptions!!.single().type).isEqualTo("IllegalStateException")
        assertThat(result.exceptions!!.single().value).isNull()
        val frame = result.exceptions!!.single().stacktrace!!.frames!!.single()
        assertThat(frame.function).isEqualTo("process")
        assertThat(frame.lineno).isEqualTo(42)
        assertThat(frame.vars).isNullOrEmpty()
        assertThat(frame.contextLine).isNull()
        assertThat(frame.absPath).isNull()
    }

    @Test
    fun `breadcrumb에는 앱 로그의 순서와 분류만 남기고 HTTP와 payload는 버린다`() {
        val source = Breadcrumb.info("token=secret").apply {
            category = "io.plady.moimyeon.worker.NotificationWorker"
            setData("email", "private@example.com")
        }

        val result = filter.breadcrumb(source)!!

        assertThat(result.category).isEqualTo(source.category)
        assertThat(result.message).doesNotContain("secret")
        assertThat(result.data).isEmpty()
        assertThat(filter.breadcrumb(Breadcrumb.http("https://api.example?token=secret", "GET"))).isNull()
    }

    @Test
    fun `Logs는 허용된 이벤트 코드만 전송하고 속성을 새로 구성한다`() {
        val arbitrary = SentryLogEvent(SentryId(), 1.0, "email=private@example.com", SentryLogLevel.INFO)
        assertThat(filter.log(arbitrary)).isNull()

        val safe = SentryLogEvent(SentryId(), 1.0, SentryPrivacyFilter.SERVICE_READY, SentryLogLevel.INFO).apply {
            setAttribute("payload", SentryLogEventAttributeValue("string", "secret"))
        }
        val result = filter.log(safe)!!

        assertThat(result.body).isEqualTo("service.ready")
        assertThat(result.attributes).containsOnlyKeys(
            "service.name",
            "deployment.environment.name",
            "service.version",
        )
    }
}
