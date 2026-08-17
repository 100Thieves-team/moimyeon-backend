package io.plady.moimyeon.security.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.security.config.AuthProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import java.net.URI
import java.time.LocalDateTime
import java.util.UUID

class OAuth2LoginSuccessHandlerTest {
    private val socialMemberResolver = mockk<SocialMemberResolver>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private val sessionIssuer = mockk<SessionIssuer>()
    private val authCookieFactory = mockk<AuthCookieFactory>()
    private val failureHandler = mockk<OAuth2LoginFailureHandler>(relaxed = true)
    private val authProperties = authProperties()
    private val handler = OAuth2LoginSuccessHandler(
        socialMemberResolver,
        jwtTokenProvider,
        sessionIssuer,
        authCookieFactory,
        authProperties,
        failureHandler,
    )

    @Test
    fun `Google 인증 성공 시 세션 쿠키를 모두 발급하고 프론트 콜백으로 이동한다`() {
        val memberId = UUID.randomUUID()
        val authentication = authentication(subject = "google-sub", email = "member@example.com")
        val issuedSession = IssuedSession(
            credential = "refresh-credential",
            expiresAt = LocalDateTime.of(2026, 8, 26, 12, 0),
        )
        val accessCookie = ResponseCookie.from(AuthCookieFactory.ACCESS_TOKEN, "access-token")
            .httpOnly(true)
            .path("/")
            .build()
        val refreshCookie = ResponseCookie.from(AuthCookieFactory.REFRESH_TOKEN, "refresh-credential")
            .httpOnly(true)
            .path(AuthCookieFactory.REFRESH_PATH)
            .build()
        every {
            socialMemberResolver.resolve(
                provider = any(),
                providerId = "google-sub",
                email = "member@example.com",
            )
        } returns AuthenticatedMember(memberId, MemberRole.USER)
        every { jwtTokenProvider.issue(memberId, MemberRole.USER) } returns "access-token"
        every { sessionIssuer.open(memberId) } returns issuedSession
        every { authCookieFactory.createAccess("access-token") } returns accessCookie
        every { authCookieFactory.createRefresh(issuedSession) } returns refreshCookie
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(MockHttpServletRequest(), response, authentication)

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo(authProperties.oauth2.successRedirectUri.toString())
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
            .containsExactly(accessCookie.toString(), refreshCookie.toString())
        verify(exactly = 0) { failureHandler.onLoginProcessingFailure(any(), any()) }
    }

    @Test
    fun `회원 확정에 실패하면 쿠키 없이 로그인 실패로 처리한다`() {
        val authentication = authentication(subject = "google-sub", email = "member@example.com")
        val failure = IllegalStateException("member provisioning failed")
        every { socialMemberResolver.resolve(any(), any(), any()) } throws failure
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(MockHttpServletRequest(), response, authentication)

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty()
        verify { failureHandler.onLoginProcessingFailure(response, failure) }
    }

    @Test
    fun `세션 발급에 실패하면 쿠키 없이 로그인 실패로 처리한다`() {
        val memberId = UUID.randomUUID()
        val authentication = authentication(subject = "google-sub", email = "member@example.com")
        val failure = IllegalStateException("session issuance failed")
        every { socialMemberResolver.resolve(any(), any(), any()) } returns
            AuthenticatedMember(memberId, MemberRole.USER)
        every { jwtTokenProvider.issue(memberId, MemberRole.USER) } returns "access-token"
        every { sessionIssuer.open(memberId) } throws failure
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(MockHttpServletRequest(), response, authentication)

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty()
        verify { failureHandler.onLoginProcessingFailure(response, failure) }
    }

    @Test
    fun `리프레시 쿠키 생성에 실패해도 액세스 쿠키를 부분 발급하지 않는다`() {
        val memberId = UUID.randomUUID()
        val authentication = authentication(subject = "google-sub", email = null)
        val issuedSession = IssuedSession(
            credential = "refresh-credential",
            expiresAt = LocalDateTime.of(2026, 8, 26, 12, 0),
        )
        val failure = IllegalStateException("refresh cookie failed")
        every { socialMemberResolver.resolve(any(), any(), any()) } returns
            AuthenticatedMember(memberId, MemberRole.USER)
        every { jwtTokenProvider.issue(memberId, MemberRole.USER) } returns "access-token"
        every { sessionIssuer.open(memberId) } returns issuedSession
        every { authCookieFactory.createAccess("access-token") } returns
            ResponseCookie.from(AuthCookieFactory.ACCESS_TOKEN, "access-token").build()
        every { authCookieFactory.createRefresh(issuedSession) } throws failure
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(MockHttpServletRequest(), response, authentication)

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty()
        verify { failureHandler.onLoginProcessingFailure(response, failure) }
    }

    private fun authentication(subject: String, email: String?): Authentication {
        val oidcUser = mockk<OidcUser>()
        every { oidcUser.subject } returns subject
        every { oidcUser.email } returns email
        return mockk {
            every { principal } returns oidcUser
        }
    }

    private fun authProperties(): AuthProperties = AuthProperties(
        cookie = AuthProperties.Cookie(
            accessTokenName = AuthCookieFactory.ACCESS_TOKEN,
            refreshTokenName = AuthCookieFactory.REFRESH_TOKEN,
            domain = "moimyeon.plady.io",
            secure = true,
            sameSite = "Lax",
            accessMaxAgeSeconds = 1800,
            refreshMaxAgeSeconds = 1209600,
        ),
        cors = AuthProperties.Cors(allowedOrigins = listOf("https://moimyeon.plady.io")),
        oauth2 = AuthProperties.OAuth2(
            successRedirectUri = URI.create("https://moimyeon.plady.io/auth/callback"),
            failureRedirectUri = URI.create("https://moimyeon.plady.io/?authError=login_failed"),
        ),
    )
}
