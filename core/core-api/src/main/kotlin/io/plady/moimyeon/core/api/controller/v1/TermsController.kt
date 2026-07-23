package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.mock.MockApiProfile
import io.plady.moimyeon.core.api.controller.v1.mock.TermsMock
import io.plady.moimyeon.core.api.controller.v1.response.TermsListResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// TODO(MOI-316): 모킹 응답. 실 구현 시 Business 레이어 호출로 교체하고 @MockApiProfile 을 제거한다.
@MockApiProfile
@RestController
class TermsController {
    @GetMapping("/v1/terms")
    fun terms(): ApiResponse<TermsListResponse> {
        return ApiResponse.success(TermsListResponse(TermsMock.terms))
    }
}
