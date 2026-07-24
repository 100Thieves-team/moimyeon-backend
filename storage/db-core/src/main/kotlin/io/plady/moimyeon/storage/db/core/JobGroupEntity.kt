package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "job_group")
class JobGroupEntity(
    val code: String,
    val displayName: String,
    val sortOrder: Short?,
    val retiredAt: LocalDateTime? = null,
) : BaseEntity()
