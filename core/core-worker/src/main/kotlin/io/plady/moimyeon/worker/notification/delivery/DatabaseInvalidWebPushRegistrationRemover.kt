package io.plady.moimyeon.worker.notification.delivery

import io.plady.moimyeon.storage.db.core.WebPushRegistrationHash
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DatabaseInvalidWebPushRegistrationRemover(
    private val repository: WebPushSubscriptionRepository,
) : InvalidWebPushRegistrationRemover {
    @Transactional
    override fun remove(registrations: Set<String>) {
        if (registrations.isEmpty()) return

        val registrationHashes = registrations.mapTo(mutableSetOf(), WebPushRegistrationHash::of)
        val expiredSubscriptions = repository.findAllByRegistrationHashIn(registrationHashes)
            .filter { it.registration in registrations }
        repository.deleteAll(expiredSubscriptions)
    }
}
