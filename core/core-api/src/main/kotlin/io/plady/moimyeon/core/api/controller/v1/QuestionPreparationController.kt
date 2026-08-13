package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateFollowUpQuestionRequest
import io.plady.moimyeon.core.api.controller.v1.request.CreateQuestionRequest
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCardSetDetailResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCardSetsResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCreatedResponse
import io.plady.moimyeon.core.api.facade.QuestionPreparationFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.question.QuestionPreparationService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class QuestionPreparationController(
    private val questionPreparationFacade: QuestionPreparationFacade,
    private val questionPreparationService: QuestionPreparationService,
) {
    @GetMapping("/v1/rooms/{roomId}/question-sets")
    fun cardSets(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<QuestionCardSetsResponse> {
        return ApiResponse.success(questionPreparationFacade.getCardSets(currentMember.id, roomId))
    }

    @GetMapping("/v1/rooms/{roomId}/question-sets/{targetMemberId}")
    fun cardSet(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @PathVariable targetMemberId: UUID,
    ): ApiResponse<QuestionCardSetDetailResponse> {
        return ApiResponse.success(
            questionPreparationFacade.getCardSet(currentMember.id, roomId, targetMemberId),
        )
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/v1/rooms/{roomId}/questions")
    fun leaveQuestion(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @RequestBody request: CreateQuestionRequest,
    ): ApiResponse<QuestionCreatedResponse> {
        val questionId = questionPreparationService.leaveQuestion(
            currentMember.id,
            roomId,
            request.targetMemberId,
            request.toContent(),
        )
        return ApiResponse.success(QuestionCreatedResponse(questionId))
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/v1/rooms/{roomId}/questions/{questionId}/follow-ups")
    fun leaveFollowUp(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @PathVariable questionId: Long,
        @RequestBody request: CreateFollowUpQuestionRequest,
    ): ApiResponse<QuestionCreatedResponse> {
        val followUpQuestionId = questionPreparationService.leaveFollowUp(
            currentMember.id,
            roomId,
            questionId,
            request.toContent(),
        )
        return ApiResponse.success(QuestionCreatedResponse(followUpQuestionId))
    }

    @DeleteMapping("/v1/rooms/{roomId}/questions/{questionId}")
    fun deleteQuestion(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @PathVariable questionId: Long,
    ): ApiResponse<Any> {
        questionPreparationService.deleteQuestion(currentMember.id, roomId, questionId)
        return ApiResponse.success()
    }
}
