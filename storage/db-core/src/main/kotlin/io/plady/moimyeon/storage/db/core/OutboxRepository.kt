package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface OutboxRepository : JpaRepository<OutboxEntity, UUID> {
    @Query(
        value = """
            SELECT *
            FROM outbox
            WHERE id = :eventId
              AND (
                relay_status = 'PENDING'
                OR (relay_status = 'PROCESSING' AND lease_until <= :now)
              )
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findClaimableByIdForUpdate(
        @Param("eventId") eventId: UUID,
        @Param("now") now: LocalDateTime,
    ): OutboxEntity?

    @Query(
        value = """
            SELECT *
            FROM outbox
            WHERE created_at <= :createdBefore
              AND (
                relay_status = 'PENDING'
                OR (relay_status = 'PROCESSING' AND lease_until <= :now)
              )
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findClaimableBatchForUpdate(
        @Param("createdBefore") createdBefore: LocalDateTime,
        @Param("now") now: LocalDateTime,
        @Param("batchSize") batchSize: Int,
    ): List<OutboxEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "DELETE FROM outbox WHERE id = :eventId AND claim_token = :claimToken",
        nativeQuery = true,
    )
    fun deleteClaimed(
        @Param("eventId") eventId: UUID,
        @Param("claimToken") claimToken: String,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE outbox
            SET relay_status = 'PENDING',
                claim_token = NULL,
                lease_until = NULL,
                updated_at = :updatedAt
            WHERE id = :eventId
              AND claim_token = :claimToken
        """,
        nativeQuery = true,
    )
    fun releaseClaim(
        @Param("eventId") eventId: UUID,
        @Param("claimToken") claimToken: String,
        @Param("updatedAt") updatedAt: LocalDateTime,
    ): Int
}
