package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateProfileRequest
import io.plady.moimyeon.core.api.controller.v1.request.UpdateProfileRequest
import io.plady.moimyeon.core.api.controller.v1.response.NicknameAvailabilityResponse
import io.plady.moimyeon.core.api.controller.v1.response.NicknameSuggestionResponse
import io.plady.moimyeon.core.api.controller.v1.response.ProfileResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ProfileController(
    private val profileService: ProfileService,
    private val catalogService: CatalogService,
) {
    @PostMapping("/v1/members/me/profile")
    fun createProfile(
        @LoginMember currentMember: CurrentMember,
        @Valid @RequestBody request: CreateProfileRequest,
    ): ApiResponse<ProfileResponse> {
        val profile = profileService.create(request.toProfile(currentMember.id))
        return ApiResponse.success(ProfileResponse.from(profile, emptyList()))
    }

    @PutMapping("/v1/members/me/profile")
    fun updateProfile(
        @LoginMember currentMember: CurrentMember,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ApiResponse<ProfileResponse> {
        val profile = profileService.update(request.toProfile(currentMember.id))
        return ApiResponse.success(ProfileResponse.from(profile, catalogService.getCompanies(profile.interestCompanyIds)))
    }

    @GetMapping("/v1/nicknames/suggestion")
    fun suggestNickname(): ApiResponse<NicknameSuggestionResponse> {
        return ApiResponse.success(NicknameSuggestionResponse(profileService.suggestNickname().value))
    }

    @GetMapping("/v1/nicknames/availability")
    fun nicknameAvailability(
        @RequestParam nickname: String,
    ): ApiResponse<NicknameAvailabilityResponse> {
        return ApiResponse.success(NicknameAvailabilityResponse(profileService.isNicknameAvailable(nickname)))
    }
}
