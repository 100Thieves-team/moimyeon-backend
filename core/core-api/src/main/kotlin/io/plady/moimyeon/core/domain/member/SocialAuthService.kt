package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.domain.terms.TermsAgreementRecorder
import io.plady.moimyeon.core.domain.terms.TermsFinder
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

@Service
class SocialAuthService(
    private val memberFinder: MemberFinder,
    private val memberManager: MemberManager,
    private val nicknameGenerator: NicknameGenerator,
    private val termsFinder: TermsFinder,
    private val termsAgreementRecorder: TermsAgreementRecorder,
    private val transactionTemplate: TransactionTemplate,
) {
    // find ~ save 사이의 동시성(따닥) -> DB 유니크 제약 조건으로 보장
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
            // 확인-후-저장 사이의 극히 드문 닉네임 동시 충돌은 새 닉네임으로 1회 재시도한다.
            // 실패한 시도는 provision 경계에서 이미 롤백됐으므로 재시도는 새 트랜잭션에서 실행된다.
            if (isNicknameConflict(e)) {
                return provision(provider, providerId, email)
            }
            // 동시 가입(따닥): (provider, providerId) 유니크 충돌 방지
            throw CoreException(CoreErrorType.SOCIAL_ACCOUNT_ALREADY_LINKED)
        }
    }

    // 신규 가입은 회원 생성(닉네임 자동 부여) + 필수 약관 자동 동의 기록이 한 트랜잭션으로 원자적이어야 한다(AC).
    // authenticate 전체를 @Transactional 로 묶으면 닉네임 충돌 예외가 트랜잭션을 rollback-only 로
    // 만들어 같은 트랜잭션 안의 재시도가 커밋될 수 없으므로, 시도 단위로 경계를 명시한다.
    private fun provision(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        // 닉네임 생성은 최대 수십 회의 점유 확인 SELECT 루프라 커밋 단위에 속하지 않는다 —
        // 트랜잭션 안에 두면 가입 트랜잭션 수명이 조회 루프에 지배된다. 유일성의 최종 보장은
        // DB 유니크 제약이고, 재시도는 provision 재호출이므로 닉네임도 새로 생성된다.
        val nickname = nicknameGenerator.generateUnique()
        return checkNotNull(
            transactionTemplate.execute {
                val memberId = memberManager.append(provider, providerId, email, nickname)
                // 가입 시점의 필수 약관을 동의 기록과 같은 스냅샷에서 읽기 위해 트랜잭션 안에 둔다.
                val requiredTerms = termsFinder.findRequiredActive()
                termsAgreementRecorder.recordAll(memberId, requiredTerms.map { it.id }, LocalDateTime.now())
                memberId
            },
        )
    }

    private fun isNicknameConflict(e: DataIntegrityViolationException): Boolean {
        return (e.rootCause?.message ?: e.message).orEmpty().contains("uk_member_nickname", ignoreCase = true)
    }
}
