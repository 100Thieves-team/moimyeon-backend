package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
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
    // 단건 조회(생성 직후 응답을 저장된 값으로 재조립하는 용도). 직무 힌트는 링크 생성 공고엔 없어 채우지 않는다.
    // 링크 생성 공고는 회사가 항상 지정돼 있으므로 companyId 는 non-null 이 보장된다.
    fun getById(jobPostingId: Long): JobPosting {
        val posting = requireFound(
            jobPostingRepository.findByIdAndDeletedAtIsNull(jobPostingId),
            CoreErrorType.JOB_POSTING_NOT_FOUND,
        )
        return JobPosting(
            id = posting.id,
            companyId = checkNotNull(posting.companyId) { "링크 생성 공고에는 companyId 가 있어야 합니다. jobPostingId=${posting.id}" },
            postingName = posting.title,
            jobRoleId = null,
            jobRoleName = null,
            sourceUrl = posting.sourceUrl,
            verified = posting.verified,
        )
    }

    // 탐색 목록의 공고 표시명 배치 조회(MOI-383). 폐기된 공고는 돌려주지 않고, 그 룸은 공고명 없이 내려간다.
    // 회사가 없는 공고도 그대로 돌려준다 — 그 룸도 목록에는 나와야 하고 회사 자리만 비운다.
    fun getLabelsByIds(ids: Collection<Long>): List<JobPostingLabel> {
        if (ids.isEmpty()) return emptyList()
        return jobPostingRepository.findByIdInAndDeletedAtIsNull(ids)
            .map { JobPostingLabel(id = it.id, companyId = it.companyId, postingName = it.title) }
    }

    // 회사에 속한 공고 id 목록(룸 탐색의 회사 필터). 룸이 회사를 직접 알지 못해 id 변환이 필요하다.
    // 비어 있으면 "그 회사의 공고가 없다"는 뜻이고, 호출자는 조회 없이 빈 결과로 끝낼 수 있다.
    fun getIdsByCompanyId(companyId: Long): List<Long> = jobPostingRepository.findIdsByCompanyId(companyId)

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
