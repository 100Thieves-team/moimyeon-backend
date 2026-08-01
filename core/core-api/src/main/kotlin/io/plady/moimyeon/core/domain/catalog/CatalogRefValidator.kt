package io.plady.moimyeon.core.domain.catalog

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.JobRoleRepository
import io.plady.moimyeon.storage.db.core.SigunguRepository
import org.springframework.stereotype.Component

@Component
class CatalogRefValidator(
    private val jobRoleRepository: JobRoleRepository,
    private val sigunguRepository: SigunguRepository,
) {
    fun validateJobRoles(jobRoleIds: Collection<Long>) {
        val distinctIds = jobRoleIds.toSet()
        if (distinctIds.isEmpty()) return
        requireBusiness(
            jobRoleRepository.countByIdInAndDeletedAtIsNull(distinctIds) == distinctIds.size.toLong(),
            CoreErrorType.JOB_ROLE_NOT_FOUND,
        )
    }

    fun validateSigungu(sigunguId: Long) {
        requireBusiness(sigunguRepository.existsByIdAndDeletedAtIsNull(sigunguId), CoreErrorType.REGION_NOT_FOUND)
    }
}
