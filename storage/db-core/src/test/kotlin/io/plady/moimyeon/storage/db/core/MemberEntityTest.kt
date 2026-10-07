package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MemberEntityTest {
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun activeMember(): MemberEntity = MemberEntity(
        id = UUID.randomUUID(),
        email = "user@example.com",
        nickname = "차분한 펭귄 12",
        status = MemberStatus.ACTIVE,
        lastLoginAt = now,
        socialAccounts = listOf(SocialAccountEntity(SocialLoginProvider.GOOGLE, "sub-1", "user@example.com")),
    )

    @Test
    fun `ACTIVE 회원을 RESTRICTED 로 전이한다`() {
        // given
        val member = activeMember()

        // when
        member.restrict()

        // then
        assertThat(member.status).isEqualTo(MemberStatus.RESTRICTED)
    }

    @Test
    fun `ACTIVE 가 아니면 restrict 는 즉시 실패한다`() {
        // given — canRestrict 판정을 건너뛴 호출은 프로그래밍 오류다
        val member = activeMember()
        member.restrict()

        // when & then
        assertThatThrownBy { member.restrict() }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(member.status).isEqualTo(MemberStatus.RESTRICTED)
    }

    @Test
    fun `RESTRICTED 회원을 ACTIVE 로 되돌린다`() {
        // given
        val member = activeMember()
        member.restrict()

        // when
        member.reactivate()

        // then
        assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `RESTRICTED 가 아니면 reactivate 는 즉시 실패한다`() {
        // given
        val member = activeMember()

        // when & then
        assertThatThrownBy { member.reactivate() }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    fun `전이 가능 여부는 can 판정으로 미리 물을 수 있다`() {
        // given
        val member = activeMember()

        // then
        assertThat(member.canRestrict()).isTrue()
        assertThat(member.canReactivate()).isFalse()

        // when
        member.restrict()

        // then
        assertThat(member.canRestrict()).isFalse()
        assertThat(member.canReactivate()).isTrue()
    }

    @Test
    fun `로그인 시각을 갱신한다`() {
        // given
        val member = activeMember()
        val later = now.plusHours(1)

        // when
        member.loggedIn(later)

        // then
        assertThat(member.lastLoginAt).isEqualTo(later)
    }

    @Test
    fun `RESTRICTED 회원도 로그인 시각은 갱신된다`() {
        // given
        val member = activeMember()
        member.restrict()
        val later = now.plusHours(1)

        // when
        member.loggedIn(later)

        // then
        assertThat(member.status).isEqualTo(MemberStatus.RESTRICTED)
        assertThat(member.lastLoginAt).isEqualTo(later)
    }
}
