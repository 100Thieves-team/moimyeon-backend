package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.auth.DevSessionCookieIssuer
import io.plady.moimyeon.core.api.controller.v1.request.IssueDevSessionRequest
import io.plady.moimyeon.core.support.response.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("(local | local-dev | dev) & !live")
class DevAuthController(
    private val devSessionCookieIssuer: DevSessionCookieIssuer,
) {
    @PostMapping("/v1/auth/dev-sessions")
    fun issue(
        @RequestBody request: IssueDevSessionRequest,
        response: HttpServletResponse,
    ): ApiResponse<Any> {
        devSessionCookieIssuer.issue(request.memberId).forEach { cookie ->
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        }
        return ApiResponse.success()
    }
}
