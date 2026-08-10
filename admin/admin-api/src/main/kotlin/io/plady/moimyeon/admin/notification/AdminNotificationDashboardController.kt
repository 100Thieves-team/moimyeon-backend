package io.plady.moimyeon.admin.notification

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Profile("!test")
@Controller
class AdminNotificationDashboardController(
    private val operationsReader: AdminNotificationOperationsReader,
) {
    @GetMapping("/admin/notifications")
    fun dashboard(model: Model): String {
        model.addAttribute(
            DASHBOARD_ATTRIBUTE,
            operationsReader.loadDashboard(RECENT_DEAD_LETTER_LIMIT),
        )
        return DASHBOARD_VIEW
    }

    private companion object {
        const val RECENT_DEAD_LETTER_LIMIT = 50
        const val DASHBOARD_ATTRIBUTE = "dashboard"
        const val DASHBOARD_VIEW = "admin/notification/dashboard"
    }
}
