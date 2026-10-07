package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.enums.ResumeSummaryStatus

sealed interface QuestionResumeSummary {
    val status: ResumeSummaryStatus

    data class Done(
        val content: String,
    ) : QuestionResumeSummary {
        override val status: ResumeSummaryStatus = ResumeSummaryStatus.DONE
    }

    data object Processing : QuestionResumeSummary {
        override val status: ResumeSummaryStatus = ResumeSummaryStatus.PROCESSING
    }

    data object Failed : QuestionResumeSummary {
        override val status: ResumeSummaryStatus = ResumeSummaryStatus.FAILED
    }
}
