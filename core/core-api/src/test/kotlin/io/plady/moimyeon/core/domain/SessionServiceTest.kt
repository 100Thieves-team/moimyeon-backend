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

class SessionServiceTest {
    private val sessionManager = mockk<SessionManager>()
    private val memberFinder = mockk<MemberFinder>()
    private val sessionService = SessionService(sessionManager, memberFinder)

    private val provider = SocialLoginProvider.GOOGLE
    private val email = Email("user@example.com")
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val memberId = UUID.randomUUID()

    @Test
    fun `유효 세션이고 활성 회원이면 memberId 를 반환한다`() {
        // given
        every { sessionManager.resolveMemberId("raw") } returns memberId
        every { memberFinder.findById(memberId) } returns Member.register(provider, "sub", email, now)

        // when
        val result = sessionService.refreshAccess("raw")

        // then
        assertThat(result).isEqualTo(memberId)
    }

    @Test
    fun `탈퇴한 회원의 세션으로는 재발급할 수 없다`() {
        // given
        every { sessionManager.resolveMemberId("raw") } returns memberId
        every { memberFinder.findById(memberId) } returns Member.register(provider, "sub", email, now).withdraw(now)

        // when & then
        assertThatThrownBy { sessionService.refreshAccess("raw") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(ErrorType.MEMBER_ALREADY_WITHDRAWN)
            }
    }

    @Test
    fun `세션은 유효하나 회원이 없으면 MEMBER_NOT_FOUND`() {
        // given
        every { sessionManager.resolveMemberId("raw") } returns memberId
        every { memberFinder.findById(memberId) } returns null

        // when & then
        assertThatThrownBy { sessionService.refreshAccess("raw") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(ErrorType.MEMBER_NOT_FOUND)
            }
    }

    @Test
    fun `로그아웃은 세션 종료를 위임한다`() {
        // given
        every { sessionManager.revoke("raw") } just Runs

        // when
        sessionService.logout("raw")

        // then
        verify(exactly = 1) { sessionManager.revoke("raw") }
    }
}
