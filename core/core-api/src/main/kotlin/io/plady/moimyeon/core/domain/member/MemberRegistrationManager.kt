package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

@Component
class MemberRegistrationManager(
    private val nicknameGenerator: NicknameGenerator,
    private val memberRegistrar: MemberRegistrar,
) {
    fun register(provider: SocialLoginProvider, providerId: String, email: Email): UUID {
        val registeredAt = LocalDateTime.now()

        repeat(MAX_ATTEMPTS) { attempt ->
            val nickname = nicknameGenerator.generateUnique()
            try {
                return memberRegistrar.register(provider, providerId, email, nickname, registeredAt)
            } catch (e: DataIntegrityViolationException) {
                when {
                    isNicknameConflict(e) && attempt == MAX_ATTEMPTS - 1 -> {
                        throw CoreException(CoreErrorType.NICKNAME_DUPLICATED)
                    }

                    isNicknameConflict(e) -> Unit
                    isSocialAccountConflict(e) -> throw CoreException(CoreErrorType.SOCIAL_ACCOUNT_ALREADY_LINKED)
                    else -> throw e
                }
            }
        }

        error("회원 가입 시도 횟수를 벗어났습니다.")
    }

    private fun isNicknameConflict(e: DataIntegrityViolationException): Boolean {
        return constraintMessage(e).contains(NICKNAME_CONSTRAINT, ignoreCase = true)
    }

    private fun isSocialAccountConflict(e: DataIntegrityViolationException): Boolean {
        return constraintMessage(e).contains(SOCIAL_ACCOUNT_CONSTRAINT, ignoreCase = true)
    }

    private fun constraintMessage(e: DataIntegrityViolationException): String {
        return (e.rootCause?.message ?: e.message).orEmpty()
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val NICKNAME_CONSTRAINT = "uk_member_nickname"
        const val SOCIAL_ACCOUNT_CONSTRAINT = "uk_social_account_provider_provider_id"
    }
}
