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
    fun submit(
        authorMemberId: UUID,
        roomId: UUID,
        content: ReviewSubmissionContent,
    ): Long {
        return submit(
            ReviewSubmissionCommand(
                roomId = roomId,
                authorMemberId = authorMemberId,
                targetMemberId = content.targetMemberId,
                tags = content.tags,
                content = content.content,
            ),
        )
    }

    fun submit(command: ReviewSubmissionCommand): Long {
        return submissionManager.submit(command)
    }

    fun update(
        authorMemberId: UUID,
        reviewId: Long,
        content: ReviewUpdateContent,
    ) {
        update(
            ReviewUpdateCommand(
                reviewId = reviewId,
                authorMemberId = authorMemberId,
                tags = content.tags,
                content = content.content,
            ),
        )
    }

    fun update(command: ReviewUpdateCommand) {
        reviewEditor.update(command)
    }

    fun delete(authorMemberId: UUID, reviewId: Long) {
        reviewEditor.delete(authorMemberId, reviewId)
    }

    fun skip(
        authorMemberId: UUID,
        roomId: UUID,
        content: ReviewSkipContent,
    ) {
        skip(
            ReviewSkipCommand(
                roomId = roomId,
                authorMemberId = authorMemberId,
                targetMemberId = content.targetMemberId,
            ),
        )
    }

    fun skip(command: ReviewSkipCommand) {
        eligibilityValidator.validate(command.roomId, command.authorMemberId, command.targetMemberId)
        skipRecorder.record(command)
    }

    fun getTargets(authorMemberId: UUID, roomId: UUID): List<ReviewTarget> {
        return targetFinder.getTargets(authorMemberId, roomId)
    }

    fun getReceivedReviewPage(
        memberId: UUID,
        lastReviewId: Long?,
        size: Int,
    ): ReceivedReviewPage {
        return receivedReviewFinder.getPage(memberId, lastReviewId, size)
    }
}
