package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

// 브라우저의 현재 푸시 전달 경로다. 해지 후 복원할 업무 데이터가 아니므로 물리 삭제한다.
@Entity
@Table(
    name = "web_push_subscription",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_web_push_subscription_registration_hash",
            columnNames = ["registration_hash"],
        ),
    ],
)
class WebPushSubscriptionEntity(
    memberId: UUID,
    @Lob
    val registration: String,
    @Column(length = 64)
    val registrationHash: String,
    registeredAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @JdbcTypeCode(SqlTypes.BINARY)
    var memberId: UUID = memberId
        protected set

    var registeredAt: LocalDateTime = registeredAt
        protected set

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN
}
