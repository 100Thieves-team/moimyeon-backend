package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.mock.NicknameMock
import io.plady.moimyeon.core.api.controller.v1.request.CreateProfileRequest
import io.plady.moimyeon.core.api.controller.v1.response.MemberMeResponse
import io.plady.moimyeon.core.api.controller.v1.response.ProfileResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.support.error.ErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

// TODO(MOI-316): 모킹 응답(무상태). 실 구현 시 Business 레이어 호출로 교체하고 @MockApiProfile 을 제거한다.
@MockApiProfile
@RestController
class MemberController {
    @GetMapping("/v1/members/me")
    fun me(
        @LoginMember currentMember: CurrentMember,
    ): ApiResponse<MemberMeResponse> {
        return ApiResponse.success(
            MemberMeResponse(
                memberId = currentMember.id,
                email = MOCK_EMAIL,
                status = MemberStatus.ACTIVE,
                profileCompleted = false,
                profile = null,
            ),
        )
    }

    @PostMapping("/v1/members/me/profile")
    fun createProfile(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateProfileRequest,
    ): ApiResponse<ProfileResponse> {
        NicknameMock.validateFormat(request.nickname)
        requireBusiness(NicknameMock.isAvailable(request.nickname), ErrorType.NICKNAME_DUPLICATED)
        return ApiResponse.success(
            ProfileResponse.completed(
                memberId = currentMember.id,
                nickname = request.nickname,
                jobTitle = request.jobTitle,
                bio = request.bio,
                meetingPreference = request.meetingPreference,
                region = request.region,
            ),
        )
    }

    companion object {
        private const val MOCK_EMAIL = "mock@moimyeon.dev"
    }
}
