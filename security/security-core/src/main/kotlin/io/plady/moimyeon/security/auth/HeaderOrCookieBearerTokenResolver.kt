package io.plady.moimyeon.security.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver

class HeaderOrCookieBearerTokenResolver(
    private val cookieName: String = AuthCookieFactory.ACCESS_TOKEN,
    private val bearerFreePaths: Set<String> = emptySet(),
) : BearerTokenResolver {
    private val headerResolver = DefaultBearerTokenResolver()

    override fun resolve(request: HttpServletRequest): String? {
        val path = request.requestURI.removePrefix(request.contextPath)
        if (path in bearerFreePaths) return null
        return headerResolver.resolve(request)
            ?: request.cookies?.firstOrNull { it.name == cookieName }?.value
    }
}
