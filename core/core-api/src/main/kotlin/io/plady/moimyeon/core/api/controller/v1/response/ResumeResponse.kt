package io.plady.moimyeon.core.api.controller.v1.response

import java.time.LocalDateTime
import java.util.UUID

data class ResumesResponse(
    val maxCount: Int,
    val resumes: List<ResumeResponse>,
)

data class ResumeResponse(
    val resumeId: UUID,
    val name: String,
    val file: ResumeFileResponse,
    val aiSummary: ResumeAiSummaryResponse,
    val isDefault: Boolean,
    val registeredAt: LocalDateTime,
)

data class ResumeFileResponse(
    val originalName: String,
    val sizeBytes: Long,
    val contentType: String,
)

data class ResumeAiSummaryResponse(
    val status: ResumeAiSummaryStatus,
    val text: String?,
)

enum class ResumeAiSummaryStatus {
    PROCESSING,
    DONE,
    FAILED,
}
