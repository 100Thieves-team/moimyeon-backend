package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.EventType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

// Outbox는 사용자가 복원할 업무 데이터가 아니라 외부 전달 전까지 보관하는 내구성 작업 기록이다.
// 전달 생명주기와 보존 정책이 별도로 생기므로 BaseEntity의 deleted_at 대신 물리 삭제를 사용한다.
@Entity
@Table(name = "outbox")
class OutboxEntity(
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    val id: UUID,
    @Enumerated(EnumType.STRING)
    val eventType: EventType,
    @Lob
    val payload: String,
) {
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN

    @Enumerated(EnumType.STRING)
    var relayStatus: OutboxRelayStatus = OutboxRelayStatus.PENDING
        protected set

    @Column(length = 36)
    var claimToken: String? = null
        protected set

    var leaseUntil: LocalDateTime? = null
        protected set

    fun claim(
        claimToken: String,
        leaseUntil: LocalDateTime,
    ) {
        check(claimToken.isNotBlank()) { "Outbox claim token must not be blank" }
        relayStatus = OutboxRelayStatus.PROCESSING
        this.claimToken = claimToken
        this.leaseUntil = leaseUntil
    }
}
