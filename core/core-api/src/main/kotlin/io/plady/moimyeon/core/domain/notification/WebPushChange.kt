package io.plady.moimyeon.core.domain.notification

sealed interface WebPushChange {
    data class Allow(
        val registration: WebPushRegistration,
    ) : WebPushChange

    data object Disallow : WebPushChange
}
