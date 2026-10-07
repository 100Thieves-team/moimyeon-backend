package io.plady.moimyeon.core.qa.controller.response

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.qa.QaResumeSummary
import java.util.UUID

data class QaResumeSummaryResponse(
    val resumeId: UUID,
    val memberId: UUID,
    val status: ResumeSummaryStatus,
    val summary: String,
    val isDefault: Boolean,
) {
    companion object {
        fun from(result: QaResumeSummary): QaResumeSummaryResponse = QaResumeSummaryResponse(
            resumeId = result.resumeId,
            memberId = result.memberId,
            status = result.status,
            summary = checkNotNull(result.content),
            isDefault = result.isDefault,
        )
    }
}
