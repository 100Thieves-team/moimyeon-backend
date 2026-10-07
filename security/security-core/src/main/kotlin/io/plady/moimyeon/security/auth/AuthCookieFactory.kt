package io.plady.moimyeon.security.auth

import io.plady.moimyeon.security.config.AuthProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AuthCookieFactory(
    private val authProperties: AuthProperties,
) {
    val accessTokenName: String = authProperties.cookie.accessTokenName
    val refreshTokenName: String = authProperties.cookie.refreshTokenName
    val restoreTokenName: String = authProperties.cookie.restoreTokenName

    fun createAccess(token: String): ResponseCookie = base(accessTokenName, token, authProperties.cookie.accessMaxAgeSeconds).path("/").build()

    fun createRefresh(session: IssuedSession): ResponseCookie = base(refreshTokenName, session.credential, authProperties.cookie.refreshMaxAgeSeconds).path(REFRESH_PATH).build()

    fun expireAccess(): ResponseCookie = base(accessTokenName, "", 0).path("/").build()

    fun expireRefresh(): ResponseCookie = base(refreshTokenName, "", 0).path(REFRESH_PATH).build()

    fun resolveRefresh(request: HttpServletRequest): String? = request.cookies?.firstOrNull { it.name == refreshTokenName }?.value

    // 복구 확인 쿠키는 복구 요청(/v1/auth/restoration)에만 실리도록 리프레시와 같은 경로로 좁힌다.
    fun createRestore(token: String): ResponseCookie = base(restoreTokenName, token, RestoreTokenProvider.TTL.seconds).path(REFRESH_PATH).build()

    fun expireRestore(): ResponseCookie = base(restoreTokenName, "", 0).path(REFRESH_PATH).build()

    fun resolveRestore(request: HttpServletRequest): String? = request.cookies?.firstOrNull { it.name == restoreTokenName }?.value

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
        const val RESTORE_TOKEN = "RESTORE_TOKEN"
        const val REFRESH_PATH = "/v1/auth"
    }
}
