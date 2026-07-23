package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "refresh_token")
class RefreshTokenEntity(
    val tokenHash: String,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    val expiresAt: LocalDateTime,
    var revokedAt: LocalDateTime? = null,
) : BaseEntity() {
    fun revoke(now: LocalDateTime) {
        if (revokedAt == null) revokedAt = now
    }

    fun isActive(now: LocalDateTime): Boolean = revokedAt == null && expiresAt.isAfter(now)
}
