package io.plady.moimyeon.admin.notification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AdminNotificationDashboardControllerTest {
    @Test
    fun `알림 운영 현황을 조회해 SSR 대시보드에 전달한다`() {
        val dashboard = AdminNotificationDashboard(
            pendingCount = 3,
            deadLetterCount = 1,
            recentDeadLetters = listOf(
                AdminDeadLetterMessage(
                    recordId = "1730000000000-0",
                    sourceRecordId = "1729999999999-0",
                    eventId = "0198b4f4-2f00-7000-8000-000000000001",
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
        )
        var requestedLimit: Int? = null
        val reader = AdminNotificationOperationsReader { limit ->
            requestedLimit = limit
            dashboard
        }
        val mockMvc = MockMvcBuilders
            .standaloneSetup(AdminNotificationDashboardController(reader))
            .build()

        val result = mockMvc.perform(get("/admin/notifications")).andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(result.modelAndView?.viewName).isEqualTo("admin/notification/dashboard")
        assertThat(result.modelAndView?.model?.get("dashboard")).isEqualTo(dashboard)
        assertThat(requestedLimit).isEqualTo(50)
    }
}
