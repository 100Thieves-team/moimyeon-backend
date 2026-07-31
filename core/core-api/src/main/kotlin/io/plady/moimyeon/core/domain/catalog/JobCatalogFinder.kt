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
    // 직무 + 직군 2쿼리를 한 스냅샷으로 읽는다.
    @Transactional(readOnly = true)
    fun findActiveGroups(): List<JobGroup> {
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

    fun findActiveRolesByIds(jobRoleIds: Collection<Long>): List<JobRole> {
        if (jobRoleIds.isEmpty()) return emptyList()
        return jobRoleRepository.findByIdInAndDeletedAtIsNull(jobRoleIds)
            .map { JobRole(it.id, it.code, it.displayName) }
    }

    fun allActiveRoles(jobRoleIds: Collection<Long>): Boolean {
        return findActiveRolesByIds(jobRoleIds).size == jobRoleIds.toSet().size
    }
}
