package io.plady.moimyeon.core.domain.terms

import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.storage.db.core.TermsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TermsFinder(
    private val termsRepository: TermsRepository,
) {
    @Transactional(readOnly = true)
    fun findActive(): List<Terms> {
        return termsRepository.findByStatus(TermsStatus.ACTIVE).map(TermsMapper::toDomain)
    }

    @Transactional(readOnly = true)
    fun findRequiredActive(): List<Terms> {
        return findActive().filter { it.required }
    }
}
