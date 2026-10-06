package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface JobPostingRoleRepository : JpaRepository<JobPostingRoleEntity, Long> {
    // 호출자가 공고별 첫 행을 대표 직무로 고르므로, 직무 id 정렬이 그 선택을 결정적으로 만든다.
    fun findByJobPostingIdInOrderByJobRoleIdAsc(jobPostingIds: Collection<Long>): List<JobPostingRoleEntity>
}
