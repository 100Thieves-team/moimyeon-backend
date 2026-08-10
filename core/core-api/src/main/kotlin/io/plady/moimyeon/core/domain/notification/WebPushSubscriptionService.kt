package io.plady.moimyeon.core.domain.notification

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WebPushSubscriptionService(
    private val manager: WebPushSubscriptionManager,
) {
    fun register(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        manager.register(memberId, registration)
    }

    fun unregister(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        manager.unregister(memberId, registration)
    }
}
