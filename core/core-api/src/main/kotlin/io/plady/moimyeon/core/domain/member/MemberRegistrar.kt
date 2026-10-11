package io.plady.moimyeon.core.domain.member

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.profile.ProfileManager
import io.plady.moimyeon.core.domain.terms.TermsAgreementManager
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.event.growth.GrowthEvent
import io.plady.moimyeon.core.event.growth.GrowthEventCode
import io.plady.moimyeon.core.event.growth.GrowthEventPublisher
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class MemberRegistrar(
    private val memberRepository: MemberRepository,
    private val termsAgreementManager: TermsAgreementManager,
    private val profileManager: ProfileManager,
    private val growthEventPublisher: GrowthEventPublisher,
) {
    @Transactional
    fun register(
        provider: SocialLoginProvider,
        providerId: String,
        email: Email,
        nickname: Nickname,
        registeredAt: LocalDateTime,
    ): UUID {
        log.debug { "member.registrar.register provider=$provider" }
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
        growthEventPublisher.publish(GrowthEvent(GrowthEventCode.MEMBER_SIGNED_UP, memberId, mapOf("provider" to provider)))
        return memberId
    }
}
