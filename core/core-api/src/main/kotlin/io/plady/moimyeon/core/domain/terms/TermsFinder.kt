package io.plady.moimyeon.core.domain.terms

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.TermsRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class TermsFinder(
    private val termsRepository: TermsRepository,
    private val clock: Clock,
) {
    fun getActive(): List<Terms> {
        log.debug { "terms.finder.getActive" }
        val effectiveTerms = termsRepository.findByStatusAndEffectiveFromLessThanEqualAndDeletedAtIsNull(
            TermsStatus.ACTIVE,
            TermsPublication.now(clock),
        ).map(TermsMapper::toDomain)
        return TermsPublication.selectCurrent(effectiveTerms)
    }

    fun getById(termsId: UUID): Terms {
        log.debug { "terms.finder.getById termsId=$termsId" }
        val entity = termsRepository.findByIdAndStatusInAndEffectiveFromLessThanEqualAndDeletedAtIsNull(
            termsId,
            listOf(TermsStatus.ACTIVE, TermsStatus.DEPRECATED),
            TermsPublication.now(clock),
        )
        return TermsMapper.toDomain(requireFound(entity, CoreErrorType.TERMS_NOT_FOUND))
    }
}
