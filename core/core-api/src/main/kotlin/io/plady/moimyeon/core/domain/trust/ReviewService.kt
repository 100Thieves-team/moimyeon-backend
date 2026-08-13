package io.plady.moimyeon.core.domain.trust

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ReviewService(
    private val eligibilityValidator: ReviewEligibilityValidator,
    private val submissionManager: ReviewSubmissionManager,
    private val reviewEditor: ReviewEditor,
    private val skipRecorder: ReviewSkipRecorder,
    private val targetFinder: ReviewTargetFinder,
    private val receivedReviewFinder: ReceivedReviewFinder,
) {
    fun submit(command: ReviewSubmissionCommand): Long {
        return submissionManager.submit(command)
    }

    fun update(command: ReviewUpdateCommand) {
        reviewEditor.update(command)
    }

    fun delete(authorMemberId: UUID, reviewId: Long) {
        reviewEditor.delete(authorMemberId, reviewId)
    }

    fun skip(command: ReviewSkipCommand) {
        eligibilityValidator.validate(command.roomId, command.authorMemberId, command.targetMemberId)
        skipRecorder.record(command)
    }

    fun getTargets(authorMemberId: UUID, roomId: UUID): List<ReviewTarget> {
        return targetFinder.getTargets(authorMemberId, roomId)
    }

    fun getReceivedReviews(memberId: UUID): List<ReceivedReview> {
        return receivedReviewFinder.getAll(memberId)
    }
}
