package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.InterviewOverviewResponse
import io.plady.moimyeon.core.api.facade.InterviewOverviewFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class InterviewOverviewController(
    private val interviewOverviewFacade: InterviewOverviewFacade,
) {
    @GetMapping("/v1/members/me/rooms")
    fun getInterviewOverview(
        @LoginMember currentMember: CurrentMember,
    ): ApiResponse<InterviewOverviewResponse> {
        return ApiResponse.success(interviewOverviewFacade.getOverview(currentMember.id))
    }
}
