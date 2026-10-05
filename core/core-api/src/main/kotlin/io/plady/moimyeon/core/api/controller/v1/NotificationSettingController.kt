package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.UpdateNotificationSettingRequest
import io.plady.moimyeon.core.api.controller.v1.request.WebPushSubscriptionRequest
import io.plady.moimyeon.core.api.controller.v1.response.NotificationSettingResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.notification.NotificationSettingService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class NotificationSettingController(
    private val notificationSettingService: NotificationSettingService,
) {
    @GetMapping("/v1/members/me/notification-setting")
    fun getNotificationSetting(
        @LoginMember currentMember: CurrentMember,
    ): ApiResponse<NotificationSettingResponse> {
        return ApiResponse.success(NotificationSettingResponse.from(notificationSettingService.get(currentMember.id)))
    }

    @PatchMapping("/v1/members/me/notification-setting")
    fun updateNotificationSetting(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: UpdateNotificationSettingRequest,
    ): ApiResponse<NotificationSettingResponse> {
        return ApiResponse.success(NotificationSettingResponse.from(notificationSettingService.change(currentMember.id, request.toChange())))
    }

    @PutMapping("/v1/members/me/web-push-subscriptions")
    fun refreshWebPushSubscription(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: WebPushSubscriptionRequest,
    ): ApiResponse<Any> {
        notificationSettingService.refreshWebPush(currentMember.id, request.toRegistration())
        return ApiResponse.success()
    }
}
