package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateProfileRequest
import io.plady.moimyeon.core.api.controller.v1.request.UpdateProfileRequest
import io.plady.moimyeon.core.api.controller.v1.response.ProfileResponse
import io.plady.moimyeon.core.api.facade.ProfileFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ProfileController(
    private val profileService: ProfileService,
    private val profileFacade: ProfileFacade,
) {
    @PostMapping("/v1/members/me/profile")
    fun createProfile(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateProfileRequest,
    ): ApiResponse<ProfileResponse> {
        profileService.create(currentMember.id, request.toContent())
        return ApiResponse.success(ProfileResponse.from(profileService.getProfile(currentMember.id), emptyList()))
    }

    @PutMapping("/v1/members/me/profile")
    fun updateProfile(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: UpdateProfileRequest,
    ): ApiResponse<ProfileResponse> {
        return ApiResponse.success(profileFacade.update(currentMember.id, request.toContent()))
    }
}
