package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
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

    private fun activeMember(): Member = Member.register(SocialLoginProvider.GOOGLE, "google-sub-1", email, nickname, now)

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
    inner class Restrict {
        @Test
        fun `ACTIVE 회원을 RESTRICTED로 전이한다`() {
            // given
            val member = activeMember()

            // when
            val restricted = member.restrict()

            // then
            assertThat(restricted.status).isEqualTo(MemberStatus.RESTRICTED)
        }

        @Test
        fun `ACTIVE가 아니면 MEMBER_NOT_ACTIVE로 실패한다`() {
            // given
            val restricted = activeMember().restrict()

            // when & then
            assertThatThrownBy { restricted.restrict() }
                .isInstanceOf(CoreException::class.java)
                .extracting("errorType")
                .isEqualTo(CoreErrorType.MEMBER_NOT_ACTIVE)
        }

        @Test
        fun `상태 변경은 원본을 수정하지 않고 새 객체를 반환한다`() {
            // given
            val member = activeMember()

            // when
            val restricted = member.restrict()

            // then
            assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
            assertThat(restricted).isNotSameAs(member)
        }
    }

    @Nested
    inner class Reactivate {
        @Test
        fun `RESTRICTED 회원을 ACTIVE로 되돌린다`() {
            // given
            val restricted = activeMember().restrict()

            // when
            val reactivated = restricted.reactivate()

            // then
            assertThat(reactivated.status).isEqualTo(MemberStatus.ACTIVE)
        }

        @Test
        fun `RESTRICTED가 아니면 MEMBER_NOT_RESTRICTED로 실패한다`() {
            // given
            val member = activeMember()

            // when & then
            assertThatThrownBy { member.reactivate() }
                .isInstanceOf(CoreException::class.java)
                .extracting("errorType")
                .isEqualTo(CoreErrorType.MEMBER_NOT_RESTRICTED)
        }
    }

    @Nested
    inner class RecordLogin {
        @Test
        fun `로그인 시각을 갱신한다`() {
            // given
            val later = now.plusHours(1)

            // when
            val logged = activeMember().recordLogin(later)

            // then
            assertThat(logged.lastLoginAt).isEqualTo(later)
        }

        @Test
        fun `RESTRICTED 회원도 로그인할 수 있다`() {
            // given
            val later = now.plusHours(1)
            val restricted = activeMember().restrict()

            // when
            val logged = restricted.recordLogin(later)

            // then
            assertThat(logged.status).isEqualTo(MemberStatus.RESTRICTED)
            assertThat(logged.lastLoginAt).isEqualTo(later)
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
