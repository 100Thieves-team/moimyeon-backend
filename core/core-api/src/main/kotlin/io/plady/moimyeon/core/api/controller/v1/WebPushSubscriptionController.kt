package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.WebPushSubscriptionRequest
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.notification.WebPushSubscriptionService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class WebPushSubscriptionController(
    private val service: WebPushSubscriptionService,
) {
    @PutMapping("/v1/members/me/web-push-subscriptions")
    fun register(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: WebPushSubscriptionRequest,
    ): ApiResponse<Any> {
        service.register(currentMember.id, request.toRegistration())
        return ApiResponse.success()
    }

    @DeleteMapping("/v1/members/me/web-push-subscriptions")
    fun unregister(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: WebPushSubscriptionRequest,
    ): ApiResponse<Any> {
        service.unregister(currentMember.id, request.toRegistration())
        return ApiResponse.success()
    }
}
