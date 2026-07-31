package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.domain.profile.ProfileManager
import io.plady.moimyeon.core.domain.terms.TermsAgreementRecorder
import io.plady.moimyeon.core.domain.terms.TermsFinder
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

@Component
class MemberProvisioner(
    private val memberManager: MemberManager,
    private val nicknameGenerator: NicknameGenerator,
    private val termsFinder: TermsFinder,
    private val termsAgreementRecorder: TermsAgreementRecorder,
    private val profileManager: ProfileManager,
    private val transactionTemplate: TransactionTemplate,
) {
    fun provision(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        return try {
            attempt(provider, providerId, email)
        } catch (e: DataIntegrityViolationException) {
            when {
                // 확인-후-저장 사이의 극히 드문 닉네임 동시 충돌은 새 닉네임으로 1회 재시도한다.
                // 실패한 시도는 attempt 경계에서 이미 롤백됐으므로 재시도는 새 트랜잭션에서 실행된다.
                MemberManager.isNicknameConflict(e) -> retryOnce(provider, providerId, email)
                // 동시 가입(따닥): (provider, providerId) 유니크 충돌
                isSocialAccountConflict(e) -> throw CoreException(CoreErrorType.SOCIAL_ACCOUNT_ALREADY_LINKED)
                // 기대하지 않은 무결성 위반을 오인하지 않도록 그 외에는 전파한다.
                else -> throw e
            }
        }
    }

    private fun attempt(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        // 닉네임 생성은 최대 수십 회의 점유 확인 SELECT 루프라 커밋 단위에 속하지 않는다
        // 유일성의 최종 보장은 DB 유니크 제약이고, 재시도에서는 새 후보가 다시 생성된다.
        val nickname = nicknameGenerator.generateUnique()
        return checkNotNull(
            transactionTemplate.execute {
                val memberId = memberManager.append(provider, providerId, email, nickname)
                // 가입 시점의 필수 약관을 동의 기록과 같은 스냅샷에서 읽기 위해 트랜잭션 안에 둔다.
                val requiredTerms = termsFinder.findRequiredActive()
                termsAgreementRecorder.recordAll(memberId, requiredTerms.map { it.id }, LocalDateTime.now())
                // 프로필은 회원당 항상 하나 존재한다. 가입과 같은 커밋으로 빈 프로필을 만든다.
                profileManager.initialize(memberId)
                memberId
            },
        )
    }

    private fun retryOnce(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        return try {
            attempt(provider, providerId, email)
        } catch (e: DataIntegrityViolationException) {
            when {
                MemberManager.isNicknameConflict(e) -> throw CoreException(CoreErrorType.NICKNAME_DUPLICATED)
                isSocialAccountConflict(e) -> throw CoreException(CoreErrorType.SOCIAL_ACCOUNT_ALREADY_LINKED)
                else -> throw e
            }
        }
    }

    private fun isSocialAccountConflict(e: DataIntegrityViolationException): Boolean {
        return (e.rootCause?.message ?: e.message).orEmpty()
            .contains("uk_social_account_provider_provider_id", ignoreCase = true)
    }
}
