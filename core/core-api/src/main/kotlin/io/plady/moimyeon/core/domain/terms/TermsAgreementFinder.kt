package io.plady.moimyeon.core.domain.terms

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import io.plady.moimyeon.storage.db.core.TermsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class TermsAgreementFinder(
    private val termsRepository: TermsRepository,
    private val termsAgreementRepository: TermsAgreementRepository,
) {
    @Transactional(readOnly = true)
    fun hasAgreedAllRequiredActive(memberId: UUID): Boolean {
        log.debug { "terms-agreement.finder.hasAgreedAllRequiredActive memberId=$memberId" }
        val requiredTermsIds = termsRepository.findByRequiredIsTrueAndStatusAndDeletedAtIsNull(TermsStatus.ACTIVE)
            .map { it.id }
        if (requiredTermsIds.isEmpty()) return true

        val agreedTermsIds = termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId).map { it.termsId }.toSet()
        return requiredTermsIds.all { it in agreedTermsIds }
    }
}
