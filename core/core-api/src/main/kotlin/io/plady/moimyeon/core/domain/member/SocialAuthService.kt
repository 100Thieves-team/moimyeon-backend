package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.domain.terms.TermsAgreementRecorder
import io.plady.moimyeon.core.domain.terms.TermsFinder
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class SocialAuthService(
    private val memberFinder: MemberFinder,
    private val memberManager: MemberManager,
    private val termsFinder: TermsFinder,
    private val termsAgreementRecorder: TermsAgreementRecorder,
) {
    // find ~ save 사이의 동시성(따닥) -> DB 유니크 제약 조건으로 보장
    // 신규 가입은 회원 생성 + 필수 약관 자동 동의 기록이 한 트랜잭션으로 원자적이어야 한다(AC).
    @Transactional
    fun authenticate(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        if (memberFinder.existsBySocialAccount(provider, providerId)) {
            val member = memberFinder.getBySocialAccount(provider, providerId)
            memberManager.recordLogin(member.id)
            return member.id
        }

        requireBusiness(!memberFinder.existsWithdrawnBySocialAccount(provider, providerId), CoreErrorType.MEMBER_ALREADY_WITHDRAWN)
        return try {
            val memberId = memberManager.append(provider, providerId, email)
            val requiredTerms = termsFinder.findRequiredActive()
            termsAgreementRecorder.recordAll(memberId, requiredTerms.map { it.id }, LocalDateTime.now())
            memberId
        } catch (e: DataIntegrityViolationException) {
            // 확인-후-저장 사이의 동시 가입(따닥): (provider, providerId) 유니크 충돌 — 재로그인하면 기존 계정으로 연결된다
            throw CoreException(CoreErrorType.SOCIAL_ACCOUNT_ALREADY_LINKED)
        }
    }
}
