package io.plady.moimyeon.core.domain.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class NotificationSettingService(
    private val notificationSettingFinder: NotificationSettingFinder,
    private val notificationSettingManager: NotificationSettingManager,
) {
    fun get(memberId: UUID): NotificationSetting = notificationSettingFinder.get(memberId)

    fun change(
        memberId: UUID,
        change: NotificationSettingChange,
    ): NotificationSetting {
        log.debug { "notification-setting.change memberId=$memberId" }
        notificationSettingManager.change(memberId, change)
        return notificationSettingFinder.get(memberId)
    }

    fun refreshWebPush(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        log.debug { "notification-setting.refreshWebPush memberId=$memberId" }
        notificationSettingManager.refreshWebPush(memberId, registration)
    }
}
