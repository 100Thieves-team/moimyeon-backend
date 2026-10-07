package io.plady.moimyeon.core.domain.company

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.CompanyRepository
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class CompanyValidator(
    private val companyRepository: CompanyRepository,
) {
    fun validateSelectable(companyIds: Collection<Long>) {
        log.debug { "company.validator.validateSelectable companyIdsCount=${companyIds.size}" }
        val distinctIds = companyIds.toSet()
        if (distinctIds.isEmpty()) return
        requireBusiness(
            companyRepository.countByIdInAndVerifiedTrueAndDeletedAtIsNull(distinctIds) == distinctIds.size.toLong(),
            CoreErrorType.COMPANY_NOT_FOUND,
        )
    }
}
