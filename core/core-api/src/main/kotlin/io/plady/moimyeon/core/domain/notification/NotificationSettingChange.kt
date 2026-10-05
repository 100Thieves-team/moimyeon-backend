package io.plady.moimyeon.core.domain.notification

// null 은 바꾸지 않는다는 뜻이다.
data class NotificationSettingChange(
    val webPush: WebPushChange?,
    val isActivityEmailEnabled: Boolean?,
    val isMarketingEmailAgreed: Boolean?,
)
