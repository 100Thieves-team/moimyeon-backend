package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.notification.NotificationSetting
import java.time.LocalDateTime

data class NotificationSettingResponse(
    val isWebPushAllowed: Boolean,
    val isActivityEmailEnabled: Boolean,
    val isMarketingEmailAgreed: Boolean,
    val marketingEmailAgreedAt: LocalDateTime?,
) {
    companion object {
        fun from(setting: NotificationSetting): NotificationSettingResponse = NotificationSettingResponse(
            isWebPushAllowed = setting.isWebPushAllowed,
            isActivityEmailEnabled = setting.isActivityEmailEnabled,
            isMarketingEmailAgreed = setting.isMarketingEmailAgreed,
            marketingEmailAgreedAt = setting.marketingEmailAgreedAt,
        )
    }
}
