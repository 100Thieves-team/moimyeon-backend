package io.plady.moimyeon.security.auth

import io.plady.moimyeon.security.config.AuthProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

// 세션 크리덴셜을 담는 인증 쿠키. 속성은 프로파일별 설정(security.auth.cookie)에서 주입받는다.
@Component
class AuthCookieFactory(
    private val authProperties: AuthProperties,
) {
    val accessTokenName: String = authProperties.cookie.accessTokenName
    val refreshTokenName: String = authProperties.cookie.refreshTokenName

    fun createAccess(token: String): ResponseCookie = base(accessTokenName, token, authProperties.cookie.accessMaxAgeSeconds).path("/").build()

    fun createRefresh(session: IssuedSession): ResponseCookie = base(refreshTokenName, session.credential, authProperties.cookie.refreshMaxAgeSeconds).path(REFRESH_PATH).build()

    fun expireAccess(): ResponseCookie = base(accessTokenName, "", 0).path("/").build()

    fun expireRefresh(): ResponseCookie = base(refreshTokenName, "", 0).path(REFRESH_PATH).build()

    fun resolveRefresh(request: HttpServletRequest): String? = request.cookies?.firstOrNull { it.name == refreshTokenName }?.value

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
