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
    // 가입 시점의 필수 약관은 MemberProvisioner 가 자동 동의로 기록하므로 가입 직후에는 항상 true 다.
    // 이 판정이 false 가 되는 경로는 하나뿐이다 — 가입 이후 새 필수 약관이 ACTIVE 로 추가되는 것.
    // 재동의 강제(MOI-370)가 붙을 때 동의 화면이 "무엇이 남았는가" 를 보여주는 데 쓴다.
    // 차단 자체는 정책 버전 비교가 맡으므로 이 조회는 요청 경로에 놓지 않는다. 호출자가 없다고 지우지 말 것.
    @Transactional(readOnly = true)
    fun hasAgreedAllRequiredActive(memberId: UUID): Boolean {
        val requiredTermsIds = termsRepository.findByRequiredIsTrueAndStatusAndDeletedAtIsNull(TermsStatus.ACTIVE)
            .map { it.id }
        if (requiredTermsIds.isEmpty()) return true

        val agreedTermsIds = termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId).map { it.termsId }.toSet()
        return requiredTermsIds.all { it in agreedTermsIds }
    }
}
