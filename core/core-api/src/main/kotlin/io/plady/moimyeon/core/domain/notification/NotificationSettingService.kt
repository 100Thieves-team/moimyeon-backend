package io.plady.moimyeon.core.domain.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class NotificationSettingService(
    private val finder: NotificationSettingFinder,
    private val manager: NotificationSettingManager,
) {
    fun get(memberId: UUID): NotificationSetting = finder.get(memberId)

    fun change(
        memberId: UUID,
        change: NotificationSettingChange,
    ): NotificationSetting {
        log.debug { "notification-setting.change memberId=$memberId" }
        manager.change(memberId, change)
        return finder.get(memberId)
    }

    fun refreshWebPush(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        log.debug { "notification-setting.refreshWebPush memberId=$memberId" }
        manager.refreshWebPush(memberId, registration)
    }
}
