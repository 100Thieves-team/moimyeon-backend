package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.api.auth.DevAccessTokenIssuer
import io.plady.moimyeon.core.api.controller.v1.request.IssueDevSessionRequest
import io.plady.moimyeon.core.api.controller.v1.response.DevAccessTokenResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class DevAuthController(
    private val devAccessTokenIssuer: DevAccessTokenIssuer,
) {
    @PostMapping("/v1/auth/dev-sessions")
    fun issue(
        @RequestBody request: IssueDevSessionRequest,
    ): ApiResponse<DevAccessTokenResponse> {
        val issuedToken = devAccessTokenIssuer.issue(request.memberId)
        return ApiResponse.success(DevAccessTokenResponse(issuedToken))
    }
}
