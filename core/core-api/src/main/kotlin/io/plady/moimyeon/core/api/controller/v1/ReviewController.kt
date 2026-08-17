package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.ReceivedReviewsRequest
import io.plady.moimyeon.core.api.controller.v1.request.SkipReviewRequest
import io.plady.moimyeon.core.api.controller.v1.request.SubmitReviewRequest
import io.plady.moimyeon.core.api.controller.v1.request.UpdateReviewRequest
import io.plady.moimyeon.core.api.controller.v1.response.ReceivedReviewsResponse
import io.plady.moimyeon.core.api.controller.v1.response.ReviewSubmittedResponse
import io.plady.moimyeon.core.api.controller.v1.response.ReviewTargetsResponse
import io.plady.moimyeon.core.api.facade.ReviewFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.trust.ReviewService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ReviewController(
    private val reviewFacade: ReviewFacade,
    private val reviewService: ReviewService,
) {
    @GetMapping("/v1/rooms/{roomId}/review-targets")
    fun targets(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<ReviewTargetsResponse> {
        return ApiResponse.success(reviewFacade.getTargets(currentMember.id, roomId))
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/v1/rooms/{roomId}/reviews")
    fun submit(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @RequestBody request: SubmitReviewRequest,
    ): ApiResponse<ReviewSubmittedResponse> {
        val reviewId = reviewService.submit(currentMember.id, roomId, request.toContent())
        return ApiResponse.success(ReviewSubmittedResponse.of(reviewId))
    }

    @PutMapping("/v1/reviews/{reviewId}")
    fun update(
        @LoginMember currentMember: CurrentMember,
        @PathVariable reviewId: Long,
        @RequestBody request: UpdateReviewRequest,
    ): ApiResponse<Any> {
        reviewService.update(currentMember.id, reviewId, request.toContent())
        return ApiResponse.success()
    }

    @DeleteMapping("/v1/reviews/{reviewId}")
    fun delete(
        @LoginMember currentMember: CurrentMember,
        @PathVariable reviewId: Long,
    ): ApiResponse<Any> {
        reviewService.delete(currentMember.id, reviewId)
        return ApiResponse.success()
    }

    @PostMapping("/v1/rooms/{roomId}/review-skips")
    fun skip(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @RequestBody request: SkipReviewRequest,
    ): ApiResponse<Any> {
        reviewService.skip(currentMember.id, roomId, request.toContent())
        return ApiResponse.success()
    }

    @GetMapping("/v1/members/me/received-reviews")
    fun receivedReviews(
        @LoginMember currentMember: CurrentMember,
        @ModelAttribute request: ReceivedReviewsRequest,
    ): ApiResponse<ReceivedReviewsResponse> {
        return ApiResponse.success(
            reviewFacade.getReceivedReviews(
                currentMember.id,
                request.toLastReviewId(),
                request.toSize(),
            ),
        )
    }
}
