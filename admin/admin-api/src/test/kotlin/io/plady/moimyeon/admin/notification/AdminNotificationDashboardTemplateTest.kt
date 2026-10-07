package io.plady.moimyeon.admin.notification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

class AdminNotificationDashboardTemplateTest {
    @Test
    fun `Bootstrap 기반 DLQ 대시보드를 렌더링한다`() {
        val templateResolver = ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            templateMode = TemplateMode.HTML
            characterEncoding = Charsets.UTF_8.name()
        }
        val templateEngine = SpringTemplateEngine().apply {
            setTemplateResolver(templateResolver)
        }
        val context = Context().apply {
            setVariable(
                "dashboard",
                AdminNotificationDashboard(
                    pendingCount = 3,
                    deadLetterCount = 1,
                    recentDeadLetters = listOf(
                        AdminDeadLetterMessage(
                            recordId = "dlq-1",
                            sourceRecordId = "source-1",
                            eventId = "event-1",
                            eventType = "ROOM_APPLICATION_ACCEPTED",
                            channel = "EMAIL",
                            failureType = "RetryAttemptsExhausted",
                            originalFailureType = "EmailDeliveryException",
                            failureMessage = "이메일 전송 실패",
                            attemptCount = "5",
                            failedAt = "2026-08-11T00:00:00Z",
                            payload = "{\"applicationId\":1}",
                        ),
                    ),
                ),
            )
        }

        val html = templateEngine.process("admin/notification/dashboard", context)

        assertThat(html).contains("bootstrap@5.3.8")
        assertThat(html).contains("알림 운영 현황", "ROOM_APPLICATION_ACCEPTED", "RetryAttemptsExhausted")
        assertThat(html).contains("&quot;applicationId&quot;")
    }
}
