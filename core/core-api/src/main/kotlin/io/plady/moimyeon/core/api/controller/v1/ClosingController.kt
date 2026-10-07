package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.SubmitClosingRequest
import io.plady.moimyeon.core.api.controller.v1.response.ClosingQuestionsResponse
import io.plady.moimyeon.core.api.controller.v1.response.ClosingSubmissionResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.closing.ClosingService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ClosingController(
    private val closingService: ClosingService,
) {
    @GetMapping("/v1/closing-questions/me")
    fun myQuestions(
        @LoginMember currentMember: CurrentMember,
        @RequestParam roomId: UUID,
    ): ApiResponse<ClosingQuestionsResponse> {
        return ApiResponse.success(ClosingQuestionsResponse.from(closingService.getQuestions(currentMember.id, roomId)))
    }

    @PostMapping("/v1/closing-responses")
    fun submit(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: SubmitClosingRequest,
    ): ApiResponse<ClosingSubmissionResponse> {
        val submission = closingService.submit(currentMember.id, request.roomId, request.toEvaluations())
        return ApiResponse.success(ClosingSubmissionResponse.from(submission))
    }
}
