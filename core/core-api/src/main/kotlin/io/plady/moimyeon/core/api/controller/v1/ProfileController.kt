package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.UpdateProfileRequest
import io.plady.moimyeon.core.api.controller.v1.response.ProfileResponse
import io.plady.moimyeon.core.api.facade.ProfileFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ProfileController(
    private val profileFacade: ProfileFacade,
) {
    @PutMapping("/v1/members/me/profile")
    fun updateProfile(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: UpdateProfileRequest,
    ): ApiResponse<ProfileResponse> {
        return ApiResponse.success(profileFacade.update(currentMember.id, request.toNickname(), request.toContent()))
    }
}
