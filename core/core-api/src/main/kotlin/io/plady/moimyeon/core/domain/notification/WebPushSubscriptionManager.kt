package io.plady.moimyeon.core.domain.notification

import io.plady.moimyeon.storage.db.core.WebPushRegistrationHash
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionEntity
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class WebPushSubscriptionManager(
    private val repository: WebPushSubscriptionRepository,
    private val clock: Clock,
) {
    @Transactional
    fun register(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        val registrationHash = WebPushRegistrationHash.of(registration.value)
        val registeredAt = LocalDateTime.now(clock)
        val existing = repository.findByRegistrationHash(registrationHash)

        if (existing == null) {
            repository.save(
                WebPushSubscriptionEntity(
                    memberId = memberId,
                    registration = registration.value,
                    registrationHash = registrationHash,
                    registeredAt = registeredAt,
                ),
            )
            return
        }

        check(existing.registration == registration.value) { "웹 푸시 등록 식별자 해시 충돌" }
        existing.registerFor(memberId, registeredAt)
    }

    @Transactional
    fun unregister(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        val existing = repository.findByRegistrationHash(WebPushRegistrationHash.of(registration.value)) ?: return
        check(existing.registration == registration.value) { "웹 푸시 등록 식별자 해시 충돌" }
        if (existing.memberId == memberId) {
            repository.delete(existing)
        }
    }
}
