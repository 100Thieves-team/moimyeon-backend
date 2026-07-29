package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
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
    name = "terms_agreement",
    uniqueConstraints = [UniqueConstraint(name = "uk_terms_agreement_member_terms", columnNames = ["member_id", "terms_id"])],
)
class TermsAgreementEntity(
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    val id: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val termsId: UUID,
    val agreedAt: LocalDateTime,
) {
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN
}
