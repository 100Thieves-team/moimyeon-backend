package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface JobGroupRepository : JpaRepository<JobGroupEntity, Long> {
    fun findByDeletedAtIsNullOrderBySortOrderAsc(): List<JobGroupEntity>
}
