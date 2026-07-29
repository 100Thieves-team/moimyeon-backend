package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.enums.TermsType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
    name = "terms",
    uniqueConstraints = [UniqueConstraint(name = "uk_terms_type_version", columnNames = ["type", "version"])],
)
class TermsEntity(
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    val id: UUID,
    @Enumerated(EnumType.STRING)
    val type: TermsType,
    val version: String,
    val title: String,
    val content: String,
    val required: Boolean,
    val effectiveFrom: LocalDateTime,
    @Enumerated(EnumType.STRING)
    var status: TermsStatus,
) {
    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN
}
