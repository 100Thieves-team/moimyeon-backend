package io.plady.moimyeon.security.auth

import io.plady.moimyeon.security.config.AuthProperties
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.net.URI

class AuthCookieFactoryTest {
    private val factory = AuthCookieFactory(
        AuthProperties(
            cookie = AuthProperties.Cookie(
                accessTokenName = "DEV_ACCESS_TOKEN",
                refreshTokenName = "DEV_REFRESH_TOKEN",
                domain = "dev.moimyeon.plady.io",
                secure = true,
                sameSite = "None",
                accessMaxAgeSeconds = 1800,
                refreshMaxAgeSeconds = 1209600,
            ),
            cors = AuthProperties.Cors(allowedOrigins = listOf("https://dev.moimyeon.plady.io")),
            oauth2 = AuthProperties.OAuth2(
                successRedirectUri = URI.create("https://dev.moimyeon.plady.io/auth/callback"),
                failureRedirectUri = URI.create("https://dev.moimyeon.plady.io/?authError=login_failed"),
            ),
        ),
    )

    @Test
    fun `설정된 환경별 이름으로 인증 쿠키를 생성하고 만료한다`() {
        assertThat(factory.createAccess("access-token").name).isEqualTo("DEV_ACCESS_TOKEN")
        assertThat(factory.expireAccess().name).isEqualTo("DEV_ACCESS_TOKEN")
        assertThat(factory.expireRefresh().name).isEqualTo("DEV_REFRESH_TOKEN")
    }

    @Test
    fun `dev refresh 조회는 기존 apex 쿠키를 무시한다`() {
        val request = MockHttpServletRequest().apply {
            setCookies(
                Cookie(AuthCookieFactory.REFRESH_TOKEN, "legacy-apex-credential"),
                Cookie("DEV_REFRESH_TOKEN", "dev-credential"),
            )
        }

        assertThat(factory.resolveRefresh(request)).isEqualTo("dev-credential")
    }
}
