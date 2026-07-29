package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "job_group",
    uniqueConstraints = [UniqueConstraint(name = "uk_job_group_code", columnNames = ["code"])],
)
class JobGroupEntity(
    val code: String,
    val displayName: String,
    val sortOrder: Short?,
) : BaseEntity()
