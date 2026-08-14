package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.ConfirmFeedbackDisclosureRequest
import io.plady.moimyeon.core.api.controller.v1.request.SaveRoundFeedbackRequest
import io.plady.moimyeon.core.api.controller.v1.response.FeedbackCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.IntervieweeRoundFeedbackResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoundQuestionRecordsResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.roundfeedback.RoundFeedbackService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class RoundFeedbackController(
    private val roundFeedbackService: RoundFeedbackService,
) {
    @GetMapping("/v1/question-records/me")
    fun myQuestionRecords(
        @LoginMember currentMember: CurrentMember,
        @RequestParam roomId: UUID,
        @RequestParam intervieweeMemberId: UUID,
    ): ApiResponse<RoundQuestionRecordsResponse> {
        val records = roundFeedbackService.getMyQuestionRecords(currentMember.id, roomId, intervieweeMemberId)
        return ApiResponse.success(RoundQuestionRecordsResponse.from(records))
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/v1/final-feedbacks")
    fun leaveFinalFeedback(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: SaveRoundFeedbackRequest,
    ): ApiResponse<FeedbackCreatedResponse> {
        val id = roundFeedbackService.leaveFinalFeedback(
            currentMember.id,
            request.roomId,
            request.intervieweeMemberId,
            request.toContent(),
        )
        return ApiResponse.success(FeedbackCreatedResponse(id))
    }

    @PutMapping("/v1/self-feedbacks")
    fun leaveSelfFeedback(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: SaveRoundFeedbackRequest,
    ): ApiResponse<FeedbackCreatedResponse> {
        val id = roundFeedbackService.leaveSelfFeedback(
            currentMember.id,
            request.roomId,
            request.intervieweeMemberId,
            request.toContent(),
        )
        return ApiResponse.success(FeedbackCreatedResponse(id))
    }

    @GetMapping("/v1/round-feedbacks")
    fun intervieweeFeedback(
        @LoginMember currentMember: CurrentMember,
        @RequestParam roomId: UUID,
        @RequestParam intervieweeMemberId: UUID,
    ): ApiResponse<IntervieweeRoundFeedbackResponse> {
        val feedback = roundFeedbackService.getIntervieweeFeedback(currentMember.id, roomId, intervieweeMemberId)
        return ApiResponse.success(IntervieweeRoundFeedbackResponse.from(feedback))
    }

    @PutMapping("/v1/feedback-disclosures/{feedbackId}")
    fun confirmDisclosure(
        @LoginMember currentMember: CurrentMember,
        @PathVariable feedbackId: Long,
        @RequestBody request: ConfirmFeedbackDisclosureRequest,
    ): ApiResponse<Any> {
        roundFeedbackService.confirmFinalFeedbackDisclosure(
            currentMember.id,
            request.roomId,
            request.intervieweeMemberId,
            feedbackId,
        )
        return ApiResponse.success()
    }
}
