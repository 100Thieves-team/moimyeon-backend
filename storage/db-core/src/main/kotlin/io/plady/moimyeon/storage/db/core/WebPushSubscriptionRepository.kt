package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WebPushSubscriptionRepository : JpaRepository<WebPushSubscriptionEntity, Long> {
    fun findByRegistrationHash(registrationHash: String): WebPushSubscriptionEntity?

    fun findAllByMemberId(memberId: UUID): List<WebPushSubscriptionEntity>

    fun findAllByRegistrationHashIn(registrationHashes: Set<String>): List<WebPushSubscriptionEntity>
}
