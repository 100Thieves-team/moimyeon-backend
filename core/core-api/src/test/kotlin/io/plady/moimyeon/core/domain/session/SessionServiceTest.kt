package io.plady.moimyeon.core.domain.session

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SessionServiceTest {
    private val sessionFinder = mockk<SessionFinder>()
    private val sessionManager = mockk<SessionManager>()
    private val memberFinder = mockk<MemberFinder>()
    private val sessionService = SessionService(sessionFinder, sessionManager, memberFinder)

    private val member = Member.register(
        SocialLoginProvider.GOOGLE,
        "sub-1",
        Email("user@example.com"),
        Nickname("차분한 펭귄 12"),
        LocalDateTime.of(2026, 1, 1, 0, 0),
    )
    private val memberId = member.id

    @Test
    fun `유효 세션이고 활성 회원이면 memberId 를 반환한다`() {
        // given
        every { sessionFinder.getMemberId("raw") } returns memberId
        every { memberFinder.getById(memberId) } returns member

        // when
        val result = sessionService.refreshAccess("raw")

        // then
        assertThat(result).isEqualTo(memberId)
    }

    @Test
    fun `세션은 유효하나 회원이 없거나 탈퇴했으면 MEMBER_NOT_FOUND`() {
        // given
        every { sessionFinder.getMemberId("raw") } returns memberId
        every { memberFinder.getById(memberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        // when & then
        assertThatThrownBy { sessionService.refreshAccess("raw") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
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
