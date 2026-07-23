package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.mock.NicknameMock
import io.plady.moimyeon.core.api.controller.v1.response.NicknameAvailabilityResponse
import io.plady.moimyeon.core.api.controller.v1.response.NicknameSuggestionResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// TODO(MOI-316): 모킹 응답. 실 구현 시 Business 레이어 호출로 교체하고 @MockApiProfile 을 제거한다.
@MockApiProfile
@RestController
class NicknameController {
    @GetMapping("/v1/nicknames/suggestion")
    fun suggestion(): ApiResponse<NicknameSuggestionResponse> {
        return ApiResponse.success(NicknameSuggestionResponse(NicknameMock.SUGGESTED))
    }

    @GetMapping("/v1/nicknames/availability")
    fun availability(
        @RequestParam value: String,
    ): ApiResponse<NicknameAvailabilityResponse> {
        NicknameMock.validateFormat(value)
        return ApiResponse.success(NicknameAvailabilityResponse(NicknameMock.isAvailable(value)))
    }
}
