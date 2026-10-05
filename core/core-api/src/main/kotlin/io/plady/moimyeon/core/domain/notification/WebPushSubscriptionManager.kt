package io.plady.moimyeon.core.domain.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.storage.db.core.WebPushRegistrationHash
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

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
        log.debug { "web-push-subscription.manager.register memberId=$memberId" }
        val registrationHash = WebPushRegistrationHash.of(registration.value)
        val registeredAt = LocalDateTime.now(clock)
        repository.upsertRegistration(
            memberId = memberId,
            registration = registration.value,
            registrationHash = registrationHash,
            registeredAt = registeredAt,
        )

        val registered = checkNotNull(repository.findByRegistrationHash(registrationHash))
        check(registered.registration == registration.value) { "웹 푸시 등록 식별자 해시 충돌" }
    }

    // 같은 브라우저를 쓰던 앞사람의 알림이 이 브라우저로 계속 가지 않게 한다.
    @Transactional
    fun unregisterIfOwnedByOther(
        memberId: UUID,
        registration: WebPushRegistration,
    ) {
        log.debug { "web-push-subscription.manager.unregisterIfOwnedByOther memberId=$memberId" }
        val existing = repository.findByRegistrationHash(WebPushRegistrationHash.of(registration.value)) ?: return
        check(existing.registration == registration.value) { "웹 푸시 등록 식별자 해시 충돌" }
        if (existing.memberId != memberId) {
            repository.delete(existing)
        }
    }

    @Transactional
    fun unregisterAll(memberId: UUID) {
        log.debug { "web-push-subscription.manager.unregisterAll memberId=$memberId" }
        repository.deleteAllByMemberId(memberId)
    }
}
