package io.plady.moimyeon.security.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver

// 웹(방식 A)은 HttpOnly 쿠키로, 앱(방식 B)은 Authorization: Bearer 헤더로 토큰을 보낸다. 둘 다 수용한다.
class HeaderOrCookieBearerTokenResolver(
    private val cookieName: String = AuthCookieFactory.ACCESS_TOKEN,
) : BearerTokenResolver {
    private val headerResolver = DefaultBearerTokenResolver()

    override fun resolve(request: HttpServletRequest): String? = headerResolver.resolve(request) // Authorization 헤더 우선 (앱/방식 B)
        ?: request.cookies?.firstOrNull { it.name == cookieName }?.value // 없으면 쿠키 (웹/방식 A)
}
