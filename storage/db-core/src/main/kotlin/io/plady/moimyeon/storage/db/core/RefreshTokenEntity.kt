package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "refresh_token",
    uniqueConstraints = [UniqueConstraint(name = "uk_refresh_token_token_hash", columnNames = ["token_hash"])],
)
class RefreshTokenEntity(
    val tokenHash: String,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    val expiresAt: LocalDateTime,
    revokedAt: LocalDateTime? = null,
) {
    // 최초 폐기 시각은 덮어쓰지 않는다(revoke 의 null 가드) — 대입 경로를 의도 메서드로 좁힌다.
    var revokedAt: LocalDateTime? = revokedAt
        protected set

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN

    fun revoke(now: LocalDateTime) {
        if (revokedAt == null) revokedAt = now
    }

    fun isActive(now: LocalDateTime): Boolean = revokedAt == null && expiresAt.isAfter(now)
}
