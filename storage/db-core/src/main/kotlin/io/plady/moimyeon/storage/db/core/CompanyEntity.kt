package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "company")
class CompanyEntity(
    val corpCode: String?,
    val nameKr: String,
    val nameNormalized: String?,
    val retiredAt: LocalDateTime? = null,
) : BaseEntity()
