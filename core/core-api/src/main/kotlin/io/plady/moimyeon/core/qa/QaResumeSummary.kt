package io.plady.moimyeon.core.qa

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import java.util.UUID

data class QaResumeSummary(
    val resumeId: UUID,
    val memberId: UUID,
    val status: ResumeSummaryStatus,
    val content: String?,
    val isDefault: Boolean,
)
