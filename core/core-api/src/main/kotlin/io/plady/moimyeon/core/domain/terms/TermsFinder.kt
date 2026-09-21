package io.plady.moimyeon.core.domain.terms

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.storage.db.core.TermsRepository
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class TermsFinder(
    private val termsRepository: TermsRepository,
) {
    fun findActive(): List<Terms> {
        log.debug { "terms.finder.findActive" }
        return termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.ACTIVE).map(TermsMapper::toDomain)
    }
}
