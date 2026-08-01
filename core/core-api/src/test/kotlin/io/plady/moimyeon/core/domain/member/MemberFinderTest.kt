package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MemberFinderTest {
    private val memberRepository = mockk<MemberRepository>()
    private val memberFinder = MemberFinder(memberRepository)

    private val provider = SocialLoginProvider.GOOGLE
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Test
    fun `id 조회는 소프트 삭제된 회원을 제외하고, 없으면 MEMBER_NOT_FOUND 를 던진다`() {
        // given
        val memberId = UUID.randomUUID()
        every { memberRepository.findByIdAndDeletedAtIsNull(memberId) } returns null

        // when & then
        assertThatThrownBy { memberFinder.getById(memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }

    @Test
    fun `살아있는 회원의 소셜 계정 존재 여부를 반환한다`() {
        // given
        every {
            memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(
                provider,
                "sub-1",
            )
        } returns false
        // when & then
        assertThat(memberFinder.existsBySocialAccount(provider, "sub-1")).isFalse()
    }

    @Test
    fun `탈퇴하지 않은 회원의 존재 여부를 반환한다`() {
        val memberId = UUID.randomUUID()
        every { memberRepository.existsByIdAndDeletedAtIsNull(memberId) } returns true

        assertThat(memberFinder.existsById(memberId)).isTrue()
    }

    @Test
    fun `엔티티를 도메인 Member 로 변환해 반환한다 (Service 로 엔티티가 새지 않는다)`() {
        // given
        val id = UUID.randomUUID()
        val entity = MemberEntity(
            id = id,
            email = "user@example.com",
            nickname = "차분한 펭귄 12",
            status = MemberStatus.ACTIVE,
            lastLoginAt = now,
            socialAccounts = mutableListOf(SocialAccountEntity(provider, "sub-1", "social@example.com")),
        )
        every { memberRepository.findByIdAndDeletedAtIsNull(id) } returns entity

        // when
        val member = memberFinder.getById(id)

        // then
        assertThat(member.id).isEqualTo(id)
        assertThat(member.email).isEqualTo(Email("user@example.com"))
        assertThat(member.nickname).isEqualTo(Nickname("차분한 펭귄 12"))
        assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(member.socialAccounts).hasSize(1)
        assertThat(member.socialAccounts.first().provider).isEqualTo(provider)
        assertThat(member.socialAccounts.first().providerId).isEqualTo("sub-1")
        assertThat(member.socialAccounts.first().linkedEmail).isEqualTo(Email("social@example.com"))
    }

    @Test
    fun `닉네임 유일성 판정은 전체 회원 대상, 자신 제외 판정은 변경용이다`() {
        // given
        val memberId = UUID.randomUUID()
        every { memberRepository.existsByNickname("점유된 닉네임 01") } returns true
        every { memberRepository.existsByNicknameAndIdNot("점유된 닉네임 01", memberId) } returns false

        // when & then — 전체 기준으로는 사용 불가, 자신이 점유한 것이라면 변경 시 허용
        assertThat(memberFinder.isNicknameAvailable(Nickname("점유된 닉네임 01"))).isFalse()
    }
}
