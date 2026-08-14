package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateQuestionCommentRequest
import io.plady.moimyeon.core.api.controller.v1.request.EditQuestionCommentRequest
import io.plady.moimyeon.core.api.controller.v1.request.GetQuestionCommentsRequest
import io.plady.moimyeon.core.api.controller.v1.request.ToggleQuestionCommentTypeRequest
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCommentCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCommentsResponse
import io.plady.moimyeon.core.api.facade.QuestionCommentFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.question.QuestionCommentService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class QuestionCommentController(
    private val questionCommentService: QuestionCommentService,
    private val questionCommentFacade: QuestionCommentFacade,
) {
    @GetMapping("/v1/question-comments")
    fun comments(
        @LoginMember currentMember: CurrentMember,
        @ModelAttribute request: GetQuestionCommentsRequest,
    ): ApiResponse<QuestionCommentsResponse> {
        return ApiResponse.success(
            questionCommentFacade.getComments(
                currentMember.id,
                request.roomId,
                request.intervieweeMemberId,
                request.questionId,
                request.toCursor(),
            ),
        )
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/v1/question-comments")
    fun leaveComment(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateQuestionCommentRequest,
    ): ApiResponse<QuestionCommentCreatedResponse> {
        val id = questionCommentService.leaveComment(
            currentMember.id,
            request.roomId,
            request.intervieweeMemberId,
            request.questionId,
            request.toType(),
            request.toContent(),
        )
        return ApiResponse.success(QuestionCommentCreatedResponse(id))
    }

    @PatchMapping("/v1/question-comment-types/{commentId}")
    fun toggleType(
        @LoginMember currentMember: CurrentMember,
        @PathVariable commentId: Long,
        @RequestBody request: ToggleQuestionCommentTypeRequest,
    ): ApiResponse<Any> {
        questionCommentService.toggleType(
            currentMember.id,
            request.roomId,
            request.intervieweeMemberId,
            request.questionId,
            commentId,
            request.toType(),
        )
        return ApiResponse.success()
    }

    @PatchMapping("/v1/question-comments/{commentId}")
    fun editComment(
        @LoginMember currentMember: CurrentMember,
        @PathVariable commentId: Long,
        @RequestBody request: EditQuestionCommentRequest,
    ): ApiResponse<Any> {
        questionCommentService.editComment(
            currentMember.id,
            request.roomId,
            request.intervieweeMemberId,
            request.questionId,
            commentId,
            request.toContent(),
        )
        return ApiResponse.success()
    }

    @DeleteMapping("/v1/question-comments/{commentId}")
    fun deleteComment(
        @LoginMember currentMember: CurrentMember,
        @PathVariable commentId: Long,
        @RequestParam roomId: UUID,
        @RequestParam intervieweeMemberId: UUID,
        @RequestParam questionId: Long,
    ): ApiResponse<Any> {
        questionCommentService.deleteComment(
            currentMember.id,
            roomId,
            intervieweeMemberId,
            questionId,
            commentId,
        )
        return ApiResponse.success()
    }
}
