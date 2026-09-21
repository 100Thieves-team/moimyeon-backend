package io.plady.moimyeon.core.domain.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class WebPushSubscriptionService(
    private val manager: WebPushSubscriptionManager,
) {
    fun register(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        log.debug { "web-push.subscription.register memberId=$memberId" }
        manager.register(memberId, registration)
    }

    fun unregister(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        log.debug { "web-push.subscription.unregister memberId=$memberId" }
        manager.unregister(memberId, registration)
    }
}
