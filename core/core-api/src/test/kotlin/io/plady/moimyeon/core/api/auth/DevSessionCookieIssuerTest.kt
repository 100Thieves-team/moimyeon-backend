package io.plady.moimyeon.core.api.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.IssuedSession
import io.plady.moimyeon.security.auth.JwtTokenProvider
import io.plady.moimyeon.security.auth.SessionIssuer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseCookie
import java.time.LocalDateTime
import java.util.UUID

class DevSessionCookieIssuerTest {
    private val memberFinder = mockk<MemberFinder>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private val sessionIssuer = mockk<SessionIssuer>()
    private val authCookieFactory = mockk<AuthCookieFactory>()
    private val issuer = DevSessionCookieIssuer(memberFinder, jwtTokenProvider, sessionIssuer, authCookieFactory)

    @Test
    fun `회원의 현재 권한으로 액세스 토큰과 리프레시 세션 쿠키를 발급한다`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val member = mockk<Member>()
        val session = IssuedSession(
            credential = "refresh-credential",
            expiresAt = LocalDateTime.of(2026, 8, 31, 12, 0),
        )
        val accessCookie = ResponseCookie.from("DEV_ACCESS_TOKEN", "access-token").build()
        val refreshCookie = ResponseCookie.from("DEV_REFRESH_TOKEN", session.credential).build()
        every { memberFinder.getById(memberId) } returns member
        every { member.id } returns memberId
        every { member.role } returns MemberRole.ADMIN
        every { jwtTokenProvider.issue(memberId, MemberRole.ADMIN) } returns "access-token"
        every { sessionIssuer.open(memberId) } returns session
        every { authCookieFactory.createAccess("access-token") } returns accessCookie
        every { authCookieFactory.createRefresh(session) } returns refreshCookie

        val cookies = issuer.issue(memberId)

        assertThat(cookies).containsExactly(accessCookie, refreshCookie)
        verifyOrder {
            memberFinder.getById(memberId)
            jwtTokenProvider.issue(memberId, MemberRole.ADMIN)
            sessionIssuer.open(memberId)
            authCookieFactory.createAccess("access-token")
            authCookieFactory.createRefresh(session)
        }
    }

    @Test
    fun `리프레시 세션 발급이 실패하면 쿠키를 만들지 않는다`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val member = mockk<Member>()
        val failure = IllegalStateException("session issue failed")
        every { memberFinder.getById(memberId) } returns member
        every { member.id } returns memberId
        every { member.role } returns MemberRole.USER
        every { jwtTokenProvider.issue(memberId, MemberRole.USER) } returns "access-token"
        every { sessionIssuer.open(memberId) } throws failure

        assertThatThrownBy { issuer.issue(memberId) }.isSameAs(failure)

        verify(exactly = 0) {
            authCookieFactory.createAccess(any())
            authCookieFactory.createRefresh(any())
        }
    }
}
