package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface JobPostingRoleRepository : JpaRepository<JobPostingRoleEntity, Long> {
    // 공고 id 묶음의 직무 매핑을 직무 id 오름차순으로 반환한다. 공고별 첫 행을 대표 직무로 고른다(결정적).
    fun findByJobPostingIdInOrderByJobRoleIdAsc(jobPostingIds: Collection<Long>): List<JobPostingRoleEntity>
}
