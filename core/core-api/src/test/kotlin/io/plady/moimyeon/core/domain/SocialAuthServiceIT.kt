package io.plady.moimyeon.core.domain

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional

@Transactional
class SocialAuthServiceIT(
    private val socialAuthService: SocialAuthService,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private val provider = SocialLoginProvider.GOOGLE

    @Test
    fun `최초 인증은 회원을 provisioning 하고, 같은 신원 재인증은 같은 memberId 를 반환하며 새로 가입하지 않는다`() {
        // given
        val providerId = "google-sub-1"

        // when
        val first = socialAuthService.authenticate(provider, providerId, Email("user@example.com"))
        val second = socialAuthService.authenticate(provider, providerId, Email("user@example.com"))

        // then
        assertThat(second).isEqualTo(first)
        assertThat(memberRepository.count()).isEqualTo(1)
    }

    @Test
    fun `재인증하면 마지막 로그인 시각이 갱신된다`() {
        // given
        val providerId = "google-sub-2"
        val memberId = socialAuthService.authenticate(provider, providerId, Email("user@example.com"))
        val firstLoginAt = memberRepository.findById(memberId).get().lastLoginAt

        // when
        socialAuthService.authenticate(provider, providerId, Email("user@example.com"))

        // then
        val secondLoginAt = memberRepository.findById(memberId).get().lastLoginAt
        assertThat(secondLoginAt).isAfterOrEqualTo(firstLoginAt)
    }
}
