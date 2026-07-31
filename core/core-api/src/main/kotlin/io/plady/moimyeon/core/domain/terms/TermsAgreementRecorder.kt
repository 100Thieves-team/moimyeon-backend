package io.plady.moimyeon.core.domain.terms

import io.plady.moimyeon.storage.db.core.TermsAgreementEntity
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class TermsAgreementRecorder(
    private val termsAgreementRepository: TermsAgreementRepository,
) {
    @Transactional
    fun recordAll(memberId: UUID, termsIds: List<UUID>, agreedAt: LocalDateTime) {
        termsAgreementRepository.saveAll(
            termsIds.map { termsId ->
                TermsAgreementEntity(
                    id = UUID.randomUUID(),
                    memberId = memberId,
                    termsId = termsId,
                    agreedAt = agreedAt,
                )
            },
        )
    }
}
