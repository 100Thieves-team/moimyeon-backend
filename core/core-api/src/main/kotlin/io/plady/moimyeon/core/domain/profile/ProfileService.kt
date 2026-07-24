package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.terms.TermsAgreementFinder
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProfileService(
    private val memberFinder: MemberFinder,
    private val termsAgreementFinder: TermsAgreementFinder,
    private val profileFinder: ProfileFinder,
    private val profileManager: ProfileManager,
    private val nicknameGenerator: NicknameGenerator,
) {

    fun create(profile: MemberProfile): MemberProfile {
        memberFinder.getById(profile.memberId)
        requireBusiness(termsAgreementFinder.hasAgreedAllRequiredActive(profile.memberId), CoreErrorType.TERMS_NOT_AGREED)
        requireBusiness(!profileFinder.exists(profile.memberId), CoreErrorType.PROFILE_ALREADY_EXISTS)
        requireBusiness(profileFinder.isNicknameAvailable(profile.nickname), CoreErrorType.NICKNAME_DUPLICATED)

        return try {
            profileManager.append(profile)
        } catch (e: DataIntegrityViolationException) {
            // find ~ save 사이의 동시성 방지. 기대하지 않은 무결성 위반을 유니크 충돌로 오인하지 않도록 그 외에는 전파한다.
            if (profileFinder.exists(profile.memberId)) {
                throw CoreException(CoreErrorType.PROFILE_ALREADY_EXISTS)
            }
            if (isNicknameConflict(e)) {
                throw CoreException(CoreErrorType.NICKNAME_DUPLICATED)
            }
            throw e
        }
    }

    private fun isNicknameConflict(e: DataIntegrityViolationException): Boolean {
        return (e.rootCause?.message ?: e.message).orEmpty().contains("uk_member_profile_nickname", ignoreCase = true)
    }

    fun hasProfile(memberId: UUID): Boolean = profileFinder.exists(memberId)

    fun getProfile(memberId: UUID): MemberProfile = profileFinder.getProfile(memberId)

    fun suggestNickname(): Nickname {
        repeat(MAX_SUGGESTION_ATTEMPTS) {
            val candidate = nicknameGenerator.generate()
            if (profileFinder.isNicknameAvailable(candidate)) {
                return candidate
            }
        }
        return Nickname("면접자 ${UUID.randomUUID().toString().take(8)}")
    }

    fun isNicknameAvailable(rawNickname: String): Boolean {
        return profileFinder.isNicknameAvailable(Nickname(rawNickname))
    }

    companion object {
        private const val MAX_SUGGESTION_ATTEMPTS = 20
    }
}
