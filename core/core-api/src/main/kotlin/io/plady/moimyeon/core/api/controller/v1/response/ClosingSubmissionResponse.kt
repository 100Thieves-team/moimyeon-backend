package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.closing.ClosingSubmission
import java.time.LocalDateTime
import java.util.UUID

data class ClosingSubmissionResponse(
    val roomId: UUID,
    val memberId: UUID,
    val submittedAt: LocalDateTime,
) {
    companion object {
        fun from(submission: ClosingSubmission): ClosingSubmissionResponse = ClosingSubmissionResponse(
            roomId = submission.roomId,
            memberId = submission.memberId,
            submittedAt = submission.submittedAt,
        )
    }
}
