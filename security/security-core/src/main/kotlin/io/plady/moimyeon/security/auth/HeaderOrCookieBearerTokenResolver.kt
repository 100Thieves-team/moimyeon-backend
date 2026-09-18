package io.plady.moimyeon.security.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver

// 웹(방식 A)은 HttpOnly 쿠키로, 앱(방식 B)은 Authorization: Bearer 헤더로 토큰을 보낸다. 둘 다 수용한다.
class HeaderOrCookieBearerTokenResolver(
    private val cookieName: String = AuthCookieFactory.ACCESS_TOKEN,
    // refresh/logout/dev 세션 발급은 기존 AT 상태와 무관하게 호출되므로 이 경로에선 AT 를 해석하지 않는다.
    // (만료된 ACCESS_TOKEN 쿠키가 실려도 리소스서버가 검증→401 로 막지 않도록)
    private val bearerFreePaths: Set<String> = emptySet(),
) : BearerTokenResolver {
    private val headerResolver = DefaultBearerTokenResolver()

    override fun resolve(request: HttpServletRequest): String? {
        val path = request.requestURI.removePrefix(request.contextPath)
        if (path in bearerFreePaths) return null
        return headerResolver.resolve(request) // Authorization 헤더 우선 (앱/방식 B)
            ?: request.cookies?.firstOrNull { it.name == cookieName }?.value // 없으면 쿠키 (웹/방식 A)
    }
}
