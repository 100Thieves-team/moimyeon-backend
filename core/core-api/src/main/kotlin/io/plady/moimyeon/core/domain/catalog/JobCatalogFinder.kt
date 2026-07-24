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
    fun findActiveGroups(): List<JobGroup> {
        val rolesByGroup = jobRoleRepository.findByRetiredAtIsNullOrderByJobGroupIdAscSortOrderAsc()
            .groupBy { it.jobGroupId }
        return jobGroupRepository.findByRetiredAtIsNullOrderBySortOrderAsc().map { group ->
            JobGroup(
                id = group.id,
                code = group.code,
                displayName = group.displayName,
                roles = rolesByGroup[group.id].orEmpty().map { JobRole(it.id, it.code, it.displayName) },
            )
        }
    }

    @Transactional(readOnly = true)
    fun existsActiveRole(jobRoleId: Long): Boolean = jobRoleRepository.existsByIdAndRetiredAtIsNull(jobRoleId)
}
