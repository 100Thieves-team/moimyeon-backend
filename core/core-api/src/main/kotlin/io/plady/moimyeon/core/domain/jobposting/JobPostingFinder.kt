package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.storage.db.core.JobPostingRepository
import io.plady.moimyeon.storage.db.core.JobPostingRoleRepository
import io.plady.moimyeon.storage.db.core.JobRoleRepository
import org.springframework.stereotype.Component

@Component
class JobPostingFinder(
    private val jobPostingRepository: JobPostingRepository,
    private val jobPostingRoleRepository: JobPostingRoleRepository,
    private val jobRoleRepository: JobRoleRepository,
) {
    // 회사에 속한 활성 공고를 공고명으로 검색하고, 각 공고에 대표 직무(가장 작은 직무 id)를 얹어 반환한다.
    fun search(companyId: Long, query: String): List<JobPosting> {
        val postings =
            jobPostingRepository.findTop20ByCompanyIdAndTitleContainingAndIsOpenTrueAndDeletedAtIsNullOrderByPostedAtDesc(
                companyId,
                query,
            )
        if (postings.isEmpty()) return emptyList()

        val representativeRole = resolveRepresentativeRoles(postings.map { it.id })
        return postings.map { posting ->
            val role = representativeRole[posting.id]
            JobPosting(
                id = posting.id,
                companyId = companyId,
                postingName = posting.title,
                jobRoleId = role?.id,
                jobRoleName = role?.name,
                sourceUrl = posting.sourceUrl,
                verified = posting.verified,
            )
        }
    }

    // 공고별 대표 직무: 직무 id 오름차순 첫 매핑을 고르고, 직무명은 job_role 에서 조회해 채운다.
    private fun resolveRepresentativeRoles(postingIds: List<Long>): Map<Long, RepresentativeRole> {
        val mappings = jobPostingRoleRepository.findByJobPostingIdInOrderByJobRoleIdAsc(postingIds)
        if (mappings.isEmpty()) return emptyMap()

        val roleNames = jobRoleRepository.findAllById(mappings.map { it.jobRoleId }.toSet())
            .associate { it.id to it.displayName }

        return mappings.fold(linkedMapOf()) { acc, mapping ->
            // 이미 대표(더 작은 직무 id)가 잡힌 공고는 건너뛴다. 직무명이 없으면(폐기 등) 대표에서 제외한다.
            val roleName = roleNames[mapping.jobRoleId]
            if (roleName != null && !acc.containsKey(mapping.jobPostingId)) {
                acc[mapping.jobPostingId] = RepresentativeRole(mapping.jobRoleId, roleName)
            }
            acc
        }
    }

    private data class RepresentativeRole(val id: Long, val name: String)
}
