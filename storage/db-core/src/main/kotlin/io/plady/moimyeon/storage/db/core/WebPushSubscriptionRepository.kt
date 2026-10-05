package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface WebPushSubscriptionRepository : JpaRepository<WebPushSubscriptionEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            INSERT INTO web_push_subscription (
                member_id,
                registration,
                registration_hash,
                registered_at,
                created_at,
                updated_at
            ) VALUES (
                :memberId,
                :registration,
                :registrationHash,
                :registeredAt,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON DUPLICATE KEY UPDATE
                member_id = VALUES(member_id),
                registered_at = VALUES(registered_at),
                updated_at = CURRENT_TIMESTAMP
        """,
        nativeQuery = true,
    )
    fun upsertRegistration(
        @Param("memberId") memberId: UUID,
        @Param("registration") registration: String,
        @Param("registrationHash") registrationHash: String,
        @Param("registeredAt") registeredAt: LocalDateTime,
    ): Int

    fun findByRegistrationHash(registrationHash: String): WebPushSubscriptionEntity?

    fun findAllByMemberId(memberId: UUID): List<WebPushSubscriptionEntity>

    // flushAutomatically 를 빼면 같은 트랜잭션에서 먼저 바꾼 회원 수신 설정이 clear 로 버려진다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM WebPushSubscriptionEntity s WHERE s.memberId = :memberId")
    fun deleteAllByMemberId(
        @Param("memberId") memberId: UUID,
    ): Int

    fun findAllByRegistrationHashIn(registrationHashes: Set<String>): List<WebPushSubscriptionEntity>

    // 회원 탈퇴용. clearAutomatically 를 켜지 않는다 — 탈퇴 트랜잭션이 잡은 회원 엔티티를 준영속으로 만들지 않는다.
    @Modifying(flushAutomatically = true)
    @Query("delete from WebPushSubscriptionEntity s where s.memberId = :memberId")
    fun deleteAllByMemberId(
        @Param("memberId") memberId: UUID,
    ): Int
}
