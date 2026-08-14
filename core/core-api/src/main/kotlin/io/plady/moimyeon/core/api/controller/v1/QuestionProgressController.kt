package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.ChangeQuestionAskedRequest
import io.plady.moimyeon.core.api.controller.v1.request.CreateProgressFollowUpQuestionRequest
import io.plady.moimyeon.core.api.controller.v1.request.CreateProgressQuestionRequest
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCreatedResponse
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.question.QuestionProgressService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class QuestionProgressController(
    private val questionProgressService: QuestionProgressService,
) {
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/v1/questions")
    fun leaveQuestion(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateProgressQuestionRequest,
    ): ApiResponse<QuestionCreatedResponse> {
        val id = questionProgressService.leaveQuestion(
            currentMember.id,
            request.roomId,
            request.intervieweeMemberId,
            request.toContent(),
        )
        return ApiResponse.success(QuestionCreatedResponse(id))
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/v1/follow-up-questions")
    fun leaveFollowUp(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateProgressFollowUpQuestionRequest,
    ): ApiResponse<QuestionCreatedResponse> {
        val id = questionProgressService.leaveFollowUp(
            currentMember.id,
            request.roomId,
            request.intervieweeMemberId,
            request.questionId,
            request.toContent(),
        )
        return ApiResponse.success(QuestionCreatedResponse(id))
    }

    @PatchMapping("/v1/questions/{questionId}")
    fun changeAsked(
        @LoginMember currentMember: CurrentMember,
        @PathVariable questionId: Long,
        @RequestBody request: ChangeQuestionAskedRequest,
    ): ApiResponse<Any> {
        questionProgressService.changeAsked(
            currentMember.id,
            request.roomId,
            request.intervieweeMemberId,
            questionId,
            request.asked,
        )
        return ApiResponse.success()
    }
}
