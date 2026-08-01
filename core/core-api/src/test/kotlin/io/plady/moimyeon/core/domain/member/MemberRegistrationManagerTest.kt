package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

class MemberRegistrationManagerTest {
    private val nicknameGenerator = mockk<NicknameGenerator>()
    private val memberRegistrar = mockk<MemberRegistrar>()
    private val memberRegistrationManager = MemberRegistrationManager(nicknameGenerator, memberRegistrar)

    private val provider = SocialLoginProvider.GOOGLE
    private val email = Email("user@example.com")
    private val nickname = Nickname("차분한 펭귄 12")

    @Test
    fun `생성한 닉네임으로 회원 등록을 요청하고 회원 id 를 반환한다`() {
        // given
        val newMemberId = UUID.randomUUID()
        every { nicknameGenerator.generateUnique() } returns nickname
        every { memberRegistrar.register(provider, "sub-1", email, nickname, any()) } returns newMemberId

        // when
        val result = memberRegistrationManager.register(provider, "sub-1", email)

        // then
        assertThat(result).isEqualTo(newMemberId)
        verify(exactly = 1) { memberRegistrar.register(provider, "sub-1", email, nickname, any()) }
    }

    @Test
    fun `닉네임 동시 충돌이 나면 새 닉네임으로 1회 재시도한다`() {
        // given
        val newMemberId = UUID.randomUUID()
        val retryNickname = Nickname("명랑한 해달 33")
        every { nicknameGenerator.generateUnique() } returns nickname andThen retryNickname
        every { memberRegistrar.register(provider, "sub-2", email, nickname, any()) } throws
            DataIntegrityViolationException("uk_member_nickname")
        every { memberRegistrar.register(provider, "sub-2", email, retryNickname, any()) } returns newMemberId

        // when
        val result = memberRegistrationManager.register(provider, "sub-2", email)

        // then
        assertThat(result).isEqualTo(newMemberId)
        verify(exactly = 2) { nicknameGenerator.generateUnique() }
    }

    @Test
    fun `재시도까지 닉네임이 충돌하면 E1007 로 닫는다`() {
        // given
        val retryNickname = Nickname("명랑한 해달 33")
        every { nicknameGenerator.generateUnique() } returns nickname andThen retryNickname
        every { memberRegistrar.register(provider, "sub-3", email, nickname, any()) } throws
            DataIntegrityViolationException("uk_member_nickname")
        every { memberRegistrar.register(provider, "sub-3", email, retryNickname, any()) } throws
            DataIntegrityViolationException("uk_member_nickname")

        // when & then
        assertThatThrownBy { memberRegistrationManager.register(provider, "sub-3", email) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
            }
    }

    @Test
    fun `동시 가입으로 소셜 계정 유니크 충돌이 나면 E1004 로 매핑한다`() {
        // given
        every { nicknameGenerator.generateUnique() } returns nickname
        every { memberRegistrar.register(provider, "sub-4", email, nickname, any()) } throws
            DataIntegrityViolationException("uk_social_account_provider_provider_id")

        // when & then
        assertThatThrownBy { memberRegistrationManager.register(provider, "sub-4", email) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.SOCIAL_ACCOUNT_ALREADY_LINKED)
            }
    }

    @Test
    fun `기대하지 않은 무결성 위반은 오인 매핑하지 않고 전파한다`() {
        // given
        every { nicknameGenerator.generateUnique() } returns nickname
        every { memberRegistrar.register(provider, "sub-5", email, nickname, any()) } throws
            DataIntegrityViolationException("NULL not allowed for column EMAIL")

        // when & then
        assertThatThrownBy { memberRegistrationManager.register(provider, "sub-5", email) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .isNotInstanceOf(CoreException::class.java)
    }
}
