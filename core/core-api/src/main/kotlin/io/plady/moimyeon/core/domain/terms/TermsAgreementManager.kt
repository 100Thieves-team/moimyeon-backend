package io.plady.moimyeon.core.domain.terms

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.storage.db.core.TermsAgreementEntity
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import io.plady.moimyeon.storage.db.core.TermsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class TermsAgreementManager(
    private val termsRepository: TermsRepository,
    private val termsAgreementRepository: TermsAgreementRepository,
    private val clock: Clock,
) {
    @Transactional
    fun agreeRequired(memberId: UUID, agreedAt: LocalDateTime) {
        log.debug { "terms-agreement.manager.agreeRequired memberId=$memberId" }
        val effectiveTerms = termsRepository.findByStatusAndEffectiveFromLessThanEqualAndDeletedAtIsNull(
            TermsStatus.ACTIVE,
            TermsPublication.now(clock),
        ).map(TermsMapper::toDomain)
        val agreements = TermsPublication.selectCurrent(effectiveTerms)
            .filter { it.required }
            .map { terms ->
                TermsAgreementEntity(
                    id = UUID.randomUUID(),
                    memberId = memberId,
                    termsId = terms.id,
                    agreedAt = agreedAt,
                )
            }

        termsAgreementRepository.saveAll(agreements)
    }
}
