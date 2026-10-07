package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Transactional
@Import(SocialAuthServiceIT.PublicationClockConfiguration::class)
class SocialAuthServiceIT(
    private val socialAuthService: SocialAuthService,
    private val memberRepository: MemberRepository,
    private val termsAgreementRepository: TermsAgreementRepository,
) : ContextTest() {
    private val provider = SocialLoginProvider.GOOGLE

    @Test
    fun `신규 가입 시 현재 유효한 필수 약관 전부에 대한 동의가 버전(termsId)과 함께 기록된다`() {
        // 시행 정각의 v1.1 두 건만 동의하고, 여전히 ACTIVE인 v1.0에는 동의하지 않는다.
        val requiredTermsIds = listOf(
            UUID.fromString("f614c0bb-87b5-44e8-baf1-2b8b2ad4c1b2"),
            UUID.fromString("d91d5a8f-228a-4a56-874a-88dd532febe7"),
        )

        // when
        val memberId = socialAuthService.authenticate(provider, "google-sub-terms", Email("user@example.com")).memberId

        // then — 어떤 약관 버전에 동의했는지(termsId)가 회원별로 남는다
        val agreements = termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId)
        assertThat(agreements.map { it.termsId }).containsExactlyInAnyOrderElementsOf(requiredTermsIds)
    }

    @Test
    fun `최초 인증은 회원을 가입시키고, 같은 신원 재인증은 같은 memberId 를 반환하며 새로 가입하지 않는다`() {
        // given
        val providerId = "google-sub-1"

        // when
        val first = socialAuthService.authenticate(provider, providerId, Email("user@example.com")).memberId
        val second = socialAuthService.authenticate(provider, providerId, Email("user@example.com")).memberId

        // then
        assertThat(second).isEqualTo(first)
        assertThat(memberRepository.count()).isEqualTo(1)
    }

    @Test
    fun `가입 시 형식 규칙을 만족하는 닉네임이 자동 부여된다`() {
        // when
        val memberId = socialAuthService.authenticate(provider, "google-sub-nick", Email("user@example.com")).memberId

        // then — 부여된 닉네임은 도메인 규칙(Nickname VO)을 통과하는 값이다
        val nickname = memberRepository.findById(memberId).get().nickname
        assertThat(Nickname(nickname).value).isEqualTo(nickname)
    }

    @Test
    fun `재인증하면 마지막 로그인 시각이 갱신된다`() {
        // given
        val providerId = "google-sub-2"
        val memberId = socialAuthService.authenticate(provider, providerId, Email("user@example.com")).memberId
        val firstLoginAt = memberRepository.findById(memberId).get().lastLoginAt

        // when
        socialAuthService.authenticate(provider, providerId, Email("user@example.com")).memberId

        // then
        val secondLoginAt = memberRepository.findById(memberId).get().lastLoginAt
        assertThat(secondLoginAt).isAfterOrEqualTo(firstLoginAt)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class PublicationClockConfiguration {
        @Bean
        @Primary
        fun signupTermsPublicationClock(): Clock = Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ZoneOffset.UTC)
    }
}
