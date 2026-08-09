package io.plady.moimyeon.core.domain.catalog

import io.plady.moimyeon.storage.db.core.JobGroupRepository
import io.plady.moimyeon.storage.db.core.JobRoleRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class JobCatalogFinder(
    private val jobGroupRepository: JobGroupRepository,
    private val jobRoleRepository: JobRoleRepository,
) {
    @Transactional(readOnly = true)
    fun getJobCatalog(): List<JobGroup> {
        val rolesByGroup = jobRoleRepository.findByDeletedAtIsNullOrderByJobGroupIdAscSortOrderAsc()
            .groupBy { it.jobGroupId }
        return jobGroupRepository.findByDeletedAtIsNullOrderBySortOrderAsc().map { group ->
            JobGroup(
                id = group.id,
                code = group.code,
                displayName = group.displayName,
                roles = rolesByGroup[group.id].orEmpty().map { JobRole(it.id, it.code, it.displayName) },
            )
        }
    }

    // 탐색 목록의 직무 표시명 배치 조회(MOI-383). 폐기된 직무는 돌려주지 않고, 그 룸은 직무 없이 내려간다.
    fun getJobRolesByIds(ids: Collection<Long>): List<JobRole> {
        if (ids.isEmpty()) return emptyList()
        return jobRoleRepository.findByIdInAndDeletedAtIsNull(ids).map { JobRole(it.id, it.code, it.displayName) }
    }

    // 직무명으로 유효 직무를 검색하고 상위 직군을 얹어 반환한다(룸 생성 직무 검색). 폐기된 직군의 직무는 제외한다.
    @Transactional(readOnly = true)
    fun searchJobRoles(query: String): List<JobRoleSearchResult> {
        val roles = jobRoleRepository.findTop20ByDisplayNameContainingAndDeletedAtIsNullOrderByJobGroupIdAscSortOrderAsc(query)
        if (roles.isEmpty()) return emptyList()

        val activeGroups = jobGroupRepository.findAllById(roles.map { it.jobGroupId }.toSet())
            .filter { it.isActive() }
            .associateBy { it.id }

        return roles.mapNotNull { role ->
            val group = activeGroups[role.jobGroupId] ?: return@mapNotNull null
            JobRoleSearchResult(
                id = role.id,
                code = role.code,
                displayName = role.displayName,
                groupCode = group.code,
                groupDisplayName = group.displayName,
            )
        }
    }
}
