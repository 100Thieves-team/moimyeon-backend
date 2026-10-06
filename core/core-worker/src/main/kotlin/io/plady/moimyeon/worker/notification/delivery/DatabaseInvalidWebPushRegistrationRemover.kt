package io.plady.moimyeon.worker.notification.delivery

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.storage.db.core.WebPushRegistrationHash
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Component
class DatabaseInvalidWebPushRegistrationRemover(
    private val webPushSubscriptionRepository: WebPushSubscriptionRepository,
) : InvalidWebPushRegistrationRemover {
    @Transactional
    override fun remove(registrations: Set<String>) {
        if (registrations.isEmpty()) return

        val registrationHashes = registrations.mapTo(mutableSetOf(), WebPushRegistrationHash::of)
        val expiredSubscriptions = webPushSubscriptionRepository.findAllByRegistrationHashIn(registrationHashes)
            .filter { it.registration in registrations }
        webPushSubscriptionRepository.deleteAll(expiredSubscriptions)
        log.debug {
            "web-push.registration.remove requested=${registrations.size}" +
                " removed=${expiredSubscriptions.size}" +
                " memberIds=${expiredSubscriptions.map { it.memberId }.distinct().joinToString(",")}"
        }
    }
}
