package io.plady.moimyeon.security.auth

import io.plady.moimyeon.security.config.AuthProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

// 자체 JWT 를 담는 인증 쿠키. 속성은 프로파일별 설정(security.auth.cookie)에서 주입받는다.
// 운영: Domain=상위도메인 + Secure=true 로 서브도메인 공유 / 로컬: Domain 미지정 + Secure=false.
@Component
class AuthCookieFactory(
    private val authProperties: AuthProperties,
) {
    fun create(token: String): ResponseCookie {
        val cookie = authProperties.cookie
        var builder = ResponseCookie.from(ACCESS_TOKEN, token)
            .httpOnly(true)
            .secure(cookie.secure)
            .sameSite(cookie.sameSite)
            .path("/")
            .maxAge(Duration.ofSeconds(cookie.maxAgeSeconds))
        cookie.domain?.takeIf { it.isNotBlank() }?.let { builder = builder.domain(it) }
        return builder.build()
    }

    companion object {
        const val ACCESS_TOKEN = "ACCESS_TOKEN"
    }
}
