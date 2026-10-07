package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "job_role",
    uniqueConstraints = [UniqueConstraint(name = "uk_job_role_code", columnNames = ["code"])],
)
class JobRoleEntity(
    val jobGroupId: Long,
    val code: String,
    val displayName: String,
    val sortOrder: Short?,
) : BaseEntity()
