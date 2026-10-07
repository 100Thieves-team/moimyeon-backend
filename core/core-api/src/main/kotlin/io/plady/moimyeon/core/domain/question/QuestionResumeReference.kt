package io.plady.moimyeon.core.domain.question

import java.util.UUID

data class QuestionResumeReference(
    val targetMemberId: UUID,
    val summary: QuestionResumeSummary,
)
