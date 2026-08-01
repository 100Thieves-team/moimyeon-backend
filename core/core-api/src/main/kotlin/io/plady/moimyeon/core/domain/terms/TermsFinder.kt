package io.plady.moimyeon.core.domain.terms

import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.storage.db.core.TermsRepository
import org.springframework.stereotype.Component

@Component
class TermsFinder(
    private val termsRepository: TermsRepository,
) {
    fun findActive(): List<Terms> {
        return termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.ACTIVE).map(TermsMapper::toDomain)
    }
}
