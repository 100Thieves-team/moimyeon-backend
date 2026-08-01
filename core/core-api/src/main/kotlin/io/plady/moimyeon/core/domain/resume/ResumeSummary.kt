package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.enums.ResumeSummaryStatus

data class ResumeSummary(
    val status: ResumeSummaryStatus,
    val content: String?,
)
