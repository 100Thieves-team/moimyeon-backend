package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface JobRoleRepository : JpaRepository<JobRoleEntity, Long> {
    fun findByDeletedAtIsNullOrderByJobGroupIdAscSortOrderAsc(): List<JobRoleEntity>

    // 직무명 부분 일치로 유효(미폐기) 직무를 직군·표시순으로 최대 20건 검색한다(룸 생성 직무 검색, MOI-327).
    fun findTop20ByDisplayNameContainingAndDeletedAtIsNullOrderByJobGroupIdAscSortOrderAsc(
        displayName: String,
    ): List<JobRoleEntity>

    fun countByIdInAndDeletedAtIsNull(ids: Collection<Long>): Long
}
