package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.notification.NotificationSettingChange
import io.plady.moimyeon.core.domain.notification.WebPushChange
import io.plady.moimyeon.core.domain.notification.WebPushRegistration
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

data class UpdateNotificationSettingRequest(
    val isWebPushAllowed: Boolean? = null,
    val webPushRegistration: String? = null,
    val isActivityEmailEnabled: Boolean? = null,
    val isMarketingEmailAgreed: Boolean? = null,
) {
    fun toChange(): NotificationSettingChange {
        if (isWebPushAllowed == null && isActivityEmailEnabled == null && isMarketingEmailAgreed == null) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        val registration = webPushRegistration
        if ((isWebPushAllowed == true) != (registration != null)) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        return NotificationSettingChange(
            webPush = when (isWebPushAllowed) {
                true -> WebPushChange.Allow(WebPushRegistration(requireNotNull(registration)))
                false -> WebPushChange.Disallow
                null -> null
            },
            isActivityEmailEnabled = isActivityEmailEnabled,
            isMarketingEmailAgreed = isMarketingEmailAgreed,
        )
    }
}
