package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "company",
    uniqueConstraints = [UniqueConstraint(name = "uk_company_corp_code", columnNames = ["corp_code"])],
)
class CompanyEntity(
    val corpCode: String?,
    val nameKr: String,
    val nameNormalized: String?,
    val verified: Boolean = false,
    val createdByMemberId: UUID? = null,
) : BaseEntity()
