package io.plady.moimyeon.core.domain.closing

import java.time.LocalDateTime
import java.util.UUID

data class ClosingSubmission(
    val roomId: UUID,
    val memberId: UUID,
    val submittedAt: LocalDateTime,
)
