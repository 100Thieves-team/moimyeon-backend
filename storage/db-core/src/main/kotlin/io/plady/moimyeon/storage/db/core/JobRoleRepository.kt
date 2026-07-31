package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface JobRoleRepository : JpaRepository<JobRoleEntity, Long> {
    fun findByDeletedAtIsNullOrderByJobGroupIdAscSortOrderAsc(): List<JobRoleEntity>

    fun findByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<JobRoleEntity>

    fun countByIdInAndDeletedAtIsNull(ids: Collection<Long>): Long
}
