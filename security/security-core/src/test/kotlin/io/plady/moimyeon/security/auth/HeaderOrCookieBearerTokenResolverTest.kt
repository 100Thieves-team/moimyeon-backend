package io.plady.moimyeon.security.auth

import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class HeaderOrCookieBearerTokenResolverTest {
    private val resolver = HeaderOrCookieBearerTokenResolver(
        bearerFreePaths = setOf("/v1/auth/refresh", "/v1/auth/logout", "/v1/auth/dev-sessions"),
    )

    @Test
    fun `refresh 경로에서는 ACCESS_TOKEN 쿠키를 해석하지 않는다`() {
        // given: 만료된 AT 쿠키가 실려 있어도
        val request = MockHttpServletRequest("POST", "/v1/auth/refresh").apply {
            setCookies(Cookie(AuthCookieFactory.ACCESS_TOKEN, "expired-access-token"))
        }

        // when & then: 리졸버가 null 을 반환해 리소스서버 검증을 건너뛴다
        assertThat(resolver.resolve(request)).isNull()
    }

    @Test
    fun `일반 보호 경로에서는 ACCESS_TOKEN 쿠키를 해석한다`() {
        // given
        val request = MockHttpServletRequest("GET", "/v1/members/me").apply {
            setCookies(Cookie(AuthCookieFactory.ACCESS_TOKEN, "valid-access-token"))
        }

        // when & then
        assertThat(resolver.resolve(request)).isEqualTo("valid-access-token")
    }

    @Test
    fun `dev 세션 발급 경로에서는 기존 ACCESS_TOKEN 쿠키를 해석하지 않는다`() {
        val request = MockHttpServletRequest("POST", "/v1/auth/dev-sessions").apply {
            setCookies(Cookie(AuthCookieFactory.ACCESS_TOKEN, "expired-access-token"))
        }

        assertThat(resolver.resolve(request)).isNull()
    }

    @Test
    fun `dev 전용 쿠키 이름을 사용하면 기존 apex 쿠키를 해석하지 않는다`() {
        val devResolver = HeaderOrCookieBearerTokenResolver(cookieName = "DEV_ACCESS_TOKEN")
        val request = MockHttpServletRequest("GET", "/v1/members/me").apply {
            setCookies(
                Cookie(AuthCookieFactory.ACCESS_TOKEN, "legacy-apex-token"),
                Cookie("DEV_ACCESS_TOKEN", "dev-token"),
            )
        }

        assertThat(devResolver.resolve(request)).isEqualTo("dev-token")
    }
}
