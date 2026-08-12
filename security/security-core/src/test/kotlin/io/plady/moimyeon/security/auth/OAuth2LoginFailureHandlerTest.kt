package io.plady.moimyeon.security.auth

import io.plady.moimyeon.security.config.AuthProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import java.net.URI

class OAuth2LoginFailureHandlerTest {
    private val authProperties = AuthProperties(
        cookie = AuthProperties.Cookie(
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
    private val handler = OAuth2LoginFailureHandler(authProperties)

    @Test
    fun `Google 인증이 거절되면 원인을 노출하지 않고 프론트 실패 화면으로 이동한다`() {
        val response = MockHttpServletResponse()
        val exception = OAuth2AuthenticationException(
            OAuth2Error("access_denied", "sensitive provider description", null),
        )

        handler.onAuthenticationFailure(MockHttpServletRequest(), response, exception)

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo(authProperties.oauth2.failureRedirectUri.toString())
        assertThat(response.redirectedUrl).doesNotContain("access_denied", "sensitive")
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty()
    }

    @Test
    fun `회원과 세션 처리 중 실패해도 같은 프론트 실패 화면으로 이동한다`() {
        val response = MockHttpServletResponse()

        handler.onLoginProcessingFailure(response, IllegalStateException("internal detail"))

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo(authProperties.oauth2.failureRedirectUri.toString())
        assertThat(response.redirectedUrl).doesNotContain("internal detail")
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty()
    }
}
