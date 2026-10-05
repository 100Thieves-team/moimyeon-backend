package io.plady.moimyeon.core.domain.notification

import java.time.LocalDateTime

data class NotificationSetting(
    val isWebPushAllowed: Boolean,
    val isActivityEmailEnabled: Boolean,
    val isMarketingEmailAgreed: Boolean,
    val marketingEmailAgreedAt: LocalDateTime?,
)
