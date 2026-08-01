package io.plady.moimyeon.core.domain.company

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.CompanyRepository
import org.springframework.stereotype.Component

@Component
class CompanyValidator(
    private val companyRepository: CompanyRepository,
) {
    fun validateSelectable(companyIds: Collection<Long>) {
        val distinctIds = companyIds.toSet()
        if (distinctIds.isEmpty()) return
        requireBusiness(
            companyRepository.countByIdInAndVerifiedTrueAndDeletedAtIsNull(distinctIds) == distinctIds.size.toLong(),
            CoreErrorType.COMPANY_NOT_FOUND,
        )
    }
}
