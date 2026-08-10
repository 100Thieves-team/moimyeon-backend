package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.notification.WebPushRegistration

data class WebPushSubscriptionRequest(
    val registration: String,
) {
    fun toRegistration(): WebPushRegistration = WebPushRegistration(registration)
}
