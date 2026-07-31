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
import java.util.UUID

class SocialAuthServiceTest {
    private val memberFinder = mockk<MemberFinder>()
    private val memberManager = mockk<MemberManager>()
    private val memberProvisioner = mockk<MemberProvisioner>()
    private val socialAuthService = SocialAuthService(memberFinder, memberManager, memberProvisioner)

    private val provider = SocialLoginProvider.GOOGLE
    private val email = Email("user@example.com")

    @Test
    fun `기존 회원이면 재로그인만 기록하고 같은 id 를 반환하며 새로 가입하지 않는다`() {
        // given
        val existingId = UUID.randomUUID()
        every { memberFinder.existsBySocialAccount(provider, "sub-1") } returns true
        every { memberManager.recordLogin(provider, "sub-1") } returns existingId

        // when
        val result = socialAuthService.authenticate(provider, "sub-1", email)

        // then
        assertThat(result).isEqualTo(existingId)
        verify(exactly = 1) { memberManager.recordLogin(provider, "sub-1") }
        // Email 이 inline value class 라 any() 매처가 깨져 구체 인자로 검증.
        verify(exactly = 0) { memberProvisioner.provision(provider, "sub-1", email) }
    }

    @Test
    fun `처음 보는 신원이면 provisioning 에 위임하고 그 결과를 반환한다`() {
        // given
        val newMemberId = UUID.randomUUID()
        every { memberFinder.existsBySocialAccount(provider, "sub-2") } returns false
        every { memberFinder.existsWithdrawnBySocialAccount(provider, "sub-2") } returns false
        every { memberProvisioner.provision(provider, "sub-2", email) } returns newMemberId

        // when
        val result = socialAuthService.authenticate(provider, "sub-2", email)

        // then
        assertThat(result).isEqualTo(newMemberId)
        verify(exactly = 1) { memberProvisioner.provision(provider, "sub-2", email) }
        verify(exactly = 0) { memberManager.recordLogin(any(), any()) }
    }

    @Test
    fun `탈퇴 회원이 점유한 신원으로는 재로그인(재가입)할 수 없다`() {
        // given — 탈퇴 회원은 살아있는 회원 조회에서 걸러지고, 점유 가드에서 거부된다
        every { memberFinder.existsBySocialAccount(provider, "sub-3") } returns false
        every { memberFinder.existsWithdrawnBySocialAccount(provider, "sub-3") } returns true

        // when & then
        assertThatThrownBy { socialAuthService.authenticate(provider, "sub-3", email) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_ALREADY_WITHDRAWN)
            }
        verify(exactly = 0) { memberManager.recordLogin(any(), any()) }
        verify(exactly = 0) { memberProvisioner.provision(provider, "sub-3", email) }
    }
}
