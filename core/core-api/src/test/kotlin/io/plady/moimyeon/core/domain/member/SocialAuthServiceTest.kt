package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class SocialAuthServiceTest {
    private val memberFinder = mockk<MemberFinder>()
    private val memberManager = mockk<MemberManager>()
    private val memberRegistrationManager = mockk<MemberRegistrationManager>()
    private val socialAuthService = SocialAuthService(memberFinder, memberManager, memberRegistrationManager)

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
        verify(exactly = 0) { memberRegistrationManager.register(provider, "sub-1", email) }
    }

    @Test
    fun `처음 보는 신원이면 회원 가입에 위임하고 그 결과를 반환한다`() {
        // given
        val newMemberId = UUID.randomUUID()
        every { memberFinder.existsBySocialAccount(provider, "sub-2") } returns false
        every { memberRegistrationManager.register(provider, "sub-2", email) } returns newMemberId

        // when
        val result = socialAuthService.authenticate(provider, "sub-2", email)

        // then
        assertThat(result).isEqualTo(newMemberId)
        verify(exactly = 1) { memberRegistrationManager.register(provider, "sub-2", email) }
        verify(exactly = 0) { memberManager.recordLogin(any(), any()) }
    }
}
