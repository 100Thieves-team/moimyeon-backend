package io.plady.moimyeon.core.domain.closing

import java.util.UUID

data class ClosingSubmissionCommand(
    val roomId: UUID,
    val memberId: UUID,
    val evaluations: List<QuestionEvaluation>,
)
