package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface JobRoleRepository : JpaRepository<JobRoleEntity, Long> {
    fun findByRetiredAtIsNullOrderByJobGroupIdAscSortOrderAsc(): List<JobRoleEntity>

    fun existsByIdAndRetiredAtIsNull(id: Long): Boolean
}
