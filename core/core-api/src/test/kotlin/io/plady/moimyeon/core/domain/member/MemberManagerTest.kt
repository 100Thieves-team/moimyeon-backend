package io.plady.moimyeon.core.domain.member

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
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
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime
import java.util.UUID

class MemberManagerTest {
    private val memberRepository = mockk<MemberRepository>()
    private val memberManager = MemberManager(memberRepository)

    private val provider = SocialLoginProvider.GOOGLE

    @Test
    fun `recordLogin 은 소셜 신원으로 회원을 찾아 마지막 로그인 시각을 갱신하고 id 를 반환한다`() {
        // given
        val id = UUID.randomUUID()
        val oldLoginAt = LocalDateTime.of(2020, 1, 1, 0, 0)
        val entity = MemberEntity(
            id = id,
            email = "user@example.com",
            nickname = "차분한 펭귄 12",
            status = MemberStatus.ACTIVE,
            lastLoginAt = oldLoginAt,
            socialAccounts = mutableListOf(SocialAccountEntity(provider, "sub-1", "user@example.com")),
        )
        every {
            memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(provider, "sub-1")
        } returns entity

        // when
        val result = memberManager.recordLogin(provider, "sub-1")

        // then
        assertThat(result).isEqualTo(id)
        assertThat(entity.lastLoginAt).isAfter(oldLoginAt)
    }

    @Test
    fun `recordLogin 은 회원이 없으면 MEMBER_NOT_FOUND 예외를 던진다`() {
        // given
        every {
            memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(provider, "nope")
        } returns null

        // when & then
        assertThatThrownBy { memberManager.recordLogin(provider, "nope") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }

    @Test
    fun `changeNickname 은 닉네임을 갱신하고 즉시 flush 해 충돌을 드러낸다`() {
        // given
        val id = UUID.randomUUID()
        val entity = MemberEntity(
            id = id,
            email = "user@example.com",
            nickname = "변경 전 닉네임 01",
            status = MemberStatus.ACTIVE,
            lastLoginAt = LocalDateTime.of(2026, 1, 1, 0, 0),
            socialAccounts = mutableListOf(SocialAccountEntity(provider, "sub-1", "user@example.com")),
        )
        every { memberRepository.findByIdAndDeletedAtIsNull(id) } returns entity
        every { memberRepository.existsByNicknameAndIdNot(any(), id) } returns false
        every { memberRepository.flush() } just Runs

        // when
        memberManager.changeNickname(id, Nickname("변경 후 닉네임 02"))

        // then
        assertThat(entity.nickname).isEqualTo("변경 후 닉네임 02")
        verify(exactly = 1) { memberRepository.flush() }
    }

    @Test
    fun `동시 변경으로 유니크 충돌이 나면 E1007 로 번역하고, 그 외 무결성 위반은 전파한다`() {
        // given
        val id = UUID.randomUUID()
        val entity = MemberEntity(
            id = id,
            email = "user@example.com",
            nickname = "변경 전 닉네임 01",
            status = MemberStatus.ACTIVE,
            lastLoginAt = LocalDateTime.of(2026, 1, 1, 0, 0),
            socialAccounts = mutableListOf(SocialAccountEntity(provider, "sub-1", "user@example.com")),
        )
        every { memberRepository.findByIdAndDeletedAtIsNull(id) } returns entity
        every { memberRepository.existsByNicknameAndIdNot(any(), id) } returns false
        every { memberRepository.flush() } throws DataIntegrityViolationException("uk_member_nickname")

        // when & then — 기대한 충돌은 도메인 에러로
        assertThatThrownBy { memberManager.changeNickname(id, Nickname("명랑한 해달 33")) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
            }

        // when & then — 그 외 무결성 위반은 전파
        every { memberRepository.flush() } throws DataIntegrityViolationException("NULL not allowed for column")
        assertThatThrownBy { memberManager.changeNickname(id, Nickname("성실한 치타 77")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `restrict 는 ACTIVE 회원을 RESTRICTED 로 전이한다`() {
        // given
        val id = UUID.randomUUID()
        val entity = MemberEntity(
            id = id,
            email = "user@example.com",
            nickname = "차분한 펭귄 12",
            status = MemberStatus.ACTIVE,
            lastLoginAt = LocalDateTime.of(2026, 1, 1, 0, 0),
            socialAccounts = mutableListOf(SocialAccountEntity(provider, "sub-1", "user@example.com")),
        )
        every { memberRepository.findByIdAndDeletedAtIsNull(id) } returns entity

        // when
        memberManager.restrict(id)

        // then
        assertThat(entity.status).isEqualTo(MemberStatus.RESTRICTED)
    }

    @Test
    fun `restrict 는 ACTIVE 가 아니면 MEMBER_NOT_ACTIVE 예외를 던진다`() {
        // given
        val id = UUID.randomUUID()
        val entity = MemberEntity(
            id = id,
            email = "user@example.com",
            nickname = "차분한 펭귄 12",
            status = MemberStatus.RESTRICTED,
            lastLoginAt = LocalDateTime.of(2026, 1, 1, 0, 0),
            socialAccounts = mutableListOf(SocialAccountEntity(provider, "sub-1", "user@example.com")),
        )
        every { memberRepository.findByIdAndDeletedAtIsNull(id) } returns entity

        // when & then
        assertThatThrownBy { memberManager.restrict(id) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_ACTIVE)
            }
    }

    @Test
    fun `withdraw 는 회원을 소프트 삭제한다`() {
        // given
        val id = UUID.randomUUID()
        val now = LocalDateTime.of(2026, 1, 1, 0, 0)
        val entity = MemberEntity(
            id = id,
            email = "user@example.com",
            nickname = "차분한 펭귄 12",
            status = MemberStatus.ACTIVE,
            lastLoginAt = now,
            socialAccounts = mutableListOf(SocialAccountEntity(provider, "sub-1", "user@example.com")),
        )
        every { memberRepository.findByIdAndDeletedAtIsNull(id) } returns entity

        // when
        memberManager.withdraw(id, now)

        // then
        assertThat(entity.isDeleted()).isTrue()
    }
}
