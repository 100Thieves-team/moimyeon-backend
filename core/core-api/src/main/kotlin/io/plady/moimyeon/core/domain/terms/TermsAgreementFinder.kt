package io.plady.moimyeon.core.domain.terms

import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import io.plady.moimyeon.storage.db.core.TermsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class TermsAgreementFinder(
    private val termsRepository: TermsRepository,
    private val termsAgreementRepository: TermsAgreementRepository,
) {
    // 동의 이력 + 활성 약관 2쿼리를 한 스냅샷으로 읽는다.
    @Transactional(readOnly = true)
    fun hasAgreedAllRequiredActive(memberId: UUID): Boolean {
        val agreedTermsIds = termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId).map { it.termsId }.toSet()
        return termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.ACTIVE)
            .filter { it.required }
            .all { it.id in agreedTermsIds }
    }
}
