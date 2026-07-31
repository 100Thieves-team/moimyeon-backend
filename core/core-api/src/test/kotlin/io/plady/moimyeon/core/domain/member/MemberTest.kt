package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MemberTest {
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val email = Email("user@example.com")
    private val nickname = Nickname("차분한 펭귄 12")

    private fun socialAccount(providerId: String = "google-sub-1") = SocialAccount(SocialLoginProvider.GOOGLE, providerId, email)

    @Nested
    inner class Register {
        @Test
        fun `최초 로그인 시 ACTIVE 회원과 소셜 계정 1개가 생성된다`() {
            // when
            val member = Member.register(SocialLoginProvider.GOOGLE, "sub-1", email, nickname, now)

            // then
            assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
            assertThat(member.lastLoginAt).isEqualTo(now)
            assertThat(member.socialAccounts).hasSize(1)

            val social = member.socialAccounts.first()
            assertThat(social.provider).isEqualTo(SocialLoginProvider.GOOGLE)
            assertThat(social.providerId).isEqualTo("sub-1")
            assertThat(social.linkedEmail).isEqualTo(email)
        }

        @Test
        fun `회원마다 서로 다른 식별자가 발급된다`() {
            // when
            val a = Member.register(SocialLoginProvider.GOOGLE, "sub-1", email, nickname, now)
            val b = Member.register(SocialLoginProvider.GOOGLE, "sub-2", email, nickname, now)

            // then
            assertThat(a.id).isNotEqualTo(b.id)
        }
    }

    @Nested
    inner class Invariants {
        @Test
        fun `소셜 계정이 없으면 생성할 수 없다`() {
            // when & then
            assertThatThrownBy {
                Member(UUID.randomUUID(), email, nickname, MemberStatus.ACTIVE, emptyList(), now)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `동일한 provider_providerId 소셜 계정은 중복될 수 없다`() {
            // given
            val duplicates = listOf(socialAccount("dup"), socialAccount("dup"))

            // when & then
            assertThatThrownBy {
                Member(UUID.randomUUID(), email, nickname, MemberStatus.ACTIVE, duplicates, now)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
