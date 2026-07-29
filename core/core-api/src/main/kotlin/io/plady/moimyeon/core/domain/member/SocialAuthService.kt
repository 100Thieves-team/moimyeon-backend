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
    private val nicknameGenerator: NicknameGenerator,
    private val termsFinder: TermsFinder,
    private val termsAgreementRecorder: TermsAgreementRecorder,
) {
    // find ~ save 사이의 동시성(따닥) -> DB 유니크 제약 조건으로 보장
    // 신규 가입은 회원 생성(닉네임 자동 부여) + 필수 약관 자동 동의 기록이 한 트랜잭션으로 원자적이어야 한다(AC).
    @Transactional
    fun authenticate(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        if (memberFinder.existsBySocialAccount(provider, providerId)) {
            val member = memberFinder.getBySocialAccount(provider, providerId)
            memberManager.recordLogin(member.id)
            return member.id
        }

        requireBusiness(!memberFinder.existsWithdrawnBySocialAccount(provider, providerId), CoreErrorType.MEMBER_ALREADY_WITHDRAWN)
        return try {
            provision(provider, providerId, email)
        } catch (e: DataIntegrityViolationException) {
            // 확인-후-저장 사이의 극히 드문 닉네임 동시 충돌은 새 닉네임으로 1회 재시도한다
            if (isNicknameConflict(e)) {
                return provision(provider, providerId, email)
            }
            // 동시 가입(따닥): (provider, providerId) 유니크 충돌 방지
            throw CoreException(CoreErrorType.SOCIAL_ACCOUNT_ALREADY_LINKED)
        }
    }

    private fun provision(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        val memberId = memberManager.append(provider, providerId, email, nicknameGenerator.generateUnique())
        val requiredTerms = termsFinder.findRequiredActive()
        termsAgreementRecorder.recordAll(memberId, requiredTerms.map { it.id }, LocalDateTime.now())
        return memberId
    }

    private fun isNicknameConflict(e: DataIntegrityViolationException): Boolean {
        return (e.rootCause?.message ?: e.message).orEmpty().contains("uk_member_nickname", ignoreCase = true)
    }
}
