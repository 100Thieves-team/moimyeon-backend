package io.plady.moimyeon.core.domain

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SocialAuthServiceTest {
    private val memberFinder = mockk<MemberFinder>()
    private val memberManager = mockk<MemberManager>()
    private val socialAuthService = SocialAuthService(memberFinder, memberManager)

    private val provider = SocialLoginProvider.GOOGLE
    private val email = Email("user@example.com")

    @Test
    fun `기존 회원이면 재로그인만 기록하고 같은 id 를 반환하며 새로 가입하지 않는다`() {
        // given
        val existing = Member.register(provider, "sub-1", email, LocalDateTime.of(2026, 1, 1, 0, 0))
        every { memberFinder.findBySocialAccount(provider, "sub-1") } returns existing
        every { memberManager.recordLogin(existing.id) } just Runs

        // when
        val result = socialAuthService.authenticate(provider, "sub-1", email)

        // then
        assertThat(result).isEqualTo(existing.id)
        verify(exactly = 1) { memberManager.recordLogin(existing.id) }
        // append 는 호출되지 않는다(가입 안 함). Email 이 inline value class 라 any() 매처가 깨져 구체 인자로 검증.
        verify(exactly = 0) { memberManager.append(provider, "sub-1", email) }
    }

    @Test
    fun `처음 보는 신원이면 append 로 provisioning 하고 그 id 를 반환한다`() {
        // given
        val newMemberId = UUID.randomUUID()
        every { memberFinder.findBySocialAccount(provider, "sub-2") } returns null
        every { memberManager.append(provider, "sub-2", email) } returns newMemberId

        // when
        val result = socialAuthService.authenticate(provider, "sub-2", email)

        // then
        assertThat(result).isEqualTo(newMemberId)
        verify(exactly = 1) { memberManager.append(provider, "sub-2", email) }
        verify(exactly = 0) { memberManager.recordLogin(any()) }
    }

    @Test
    fun `탈퇴한 회원의 신원으로는 재로그인할 수 없다`() {
        // given
        val now = LocalDateTime.of(2026, 1, 1, 0, 0)
        val withdrawn = Member.register(provider, "sub-3", email, now).withdraw(now)
        every { memberFinder.findBySocialAccount(provider, "sub-3") } returns withdrawn

        // when & then
        assertThatThrownBy { socialAuthService.authenticate(provider, "sub-3", email) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(ErrorType.MEMBER_ALREADY_WITHDRAWN)
            }
        verify(exactly = 0) { memberManager.recordLogin(any()) }
        verify(exactly = 0) { memberManager.append(provider, "sub-3", email) }
    }
}
