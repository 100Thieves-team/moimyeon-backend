package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.domain.session.SessionService
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.core.support.response.ApiResponse
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.JwtTokenProvider
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val sessionService: SessionService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authCookieFactory: AuthCookieFactory,
) {
    @PostMapping("/v1/auth/refresh")
    fun refresh(
        @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<Any> {
        val credential = requireFound(refreshToken, CoreErrorType.INVALID_SESSION)
        val memberId = sessionService.refreshAccess(credential)
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.createAccess(jwtTokenProvider.issue(memberId)).toString())
        return ApiResponse.success()
    }

    @PostMapping("/v1/auth/logout")
    fun logout(
        @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN, required = false) refreshToken: String?,
        response: HttpServletResponse,
    ): ApiResponse<Any> {
        refreshToken?.let { sessionService.logout(it) }
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.expireAccess().toString())
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.expireRefresh().toString())
        return ApiResponse.success()
    }
}
