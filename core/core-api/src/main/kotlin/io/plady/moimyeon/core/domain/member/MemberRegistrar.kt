package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.domain.profile.ProfileManager
import io.plady.moimyeon.core.domain.terms.TermsAgreementManager
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class MemberRegistrar(
    private val memberRepository: MemberRepository,
    private val termsAgreementManager: TermsAgreementManager,
    private val profileManager: ProfileManager,
) {
    @Transactional
    fun register(
        provider: SocialLoginProvider,
        providerId: String,
        email: Email,
        nickname: Nickname,
        registeredAt: LocalDateTime,
    ): UUID {
        requireBusiness(
            !memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNotNull(
                provider,
                providerId,
            ),
            CoreErrorType.MEMBER_ALREADY_WITHDRAWN,
        )

        val member = Member.register(provider, providerId, email, nickname, registeredAt)
        val memberId = memberRepository.saveAndFlush(MemberMapper.toEntity(member)).id
        termsAgreementManager.agreeRequired(memberId, registeredAt)
        profileManager.createEmpty(memberId)
        return memberId
    }
}
