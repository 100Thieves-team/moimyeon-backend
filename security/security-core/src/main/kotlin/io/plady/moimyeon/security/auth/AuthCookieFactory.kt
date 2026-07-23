package io.plady.moimyeon.security.auth

import io.plady.moimyeon.security.config.AuthProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

// 세션 크리덴셜을 담는 인증 쿠키. 속성은 프로파일별 설정(security.auth.cookie)에서 주입받는다.
@Component
class AuthCookieFactory(
    private val authProperties: AuthProperties,
) {
    fun createAccess(token: String): ResponseCookie = base(ACCESS_TOKEN, token, authProperties.cookie.accessMaxAgeSeconds).path("/").build()

    fun createRefresh(session: IssuedSession): ResponseCookie = base(REFRESH_TOKEN, session.credential, authProperties.cookie.refreshMaxAgeSeconds).path(REFRESH_PATH).build()

    fun expireAccess(): ResponseCookie = base(ACCESS_TOKEN, "", 0).path("/").build()

    fun expireRefresh(): ResponseCookie = base(REFRESH_TOKEN, "", 0).path(REFRESH_PATH).build()

    // 삭제 쿠키는 생성 때와 name+Path(+Domain)가 같아야 브라우저가 지움.
    private fun base(name: String, value: String, maxAgeSeconds: Long): ResponseCookie.ResponseCookieBuilder {
        val cookie = authProperties.cookie
        var builder = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookie.secure)
            .sameSite(cookie.sameSite)
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
        cookie.domain?.takeIf { it.isNotBlank() }?.let { builder = builder.domain(it) }
        return builder
    }

    companion object {
        const val ACCESS_TOKEN = "ACCESS_TOKEN"
        const val REFRESH_TOKEN = "REFRESH_TOKEN"
        const val REFRESH_PATH = "/v1/auth"
    }
}
