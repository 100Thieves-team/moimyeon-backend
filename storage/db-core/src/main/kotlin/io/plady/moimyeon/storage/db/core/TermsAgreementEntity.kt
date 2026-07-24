package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

// append-only
@Entity
@Table(name = "terms_agreement")
class TermsAgreementEntity(
    id: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val termsId: UUID,
    val agreedAt: LocalDateTime,
) : UuidBaseEntity(id)
