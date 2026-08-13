package io.plady.moimyeon.core.api.controller.v1.response

import com.fasterxml.jackson.annotation.JsonFormat
import io.plady.moimyeon.core.domain.roomapplication.RoomApplication
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import java.time.LocalDateTime
import java.util.UUID

data class MyRoomApplicationResponse(
    val applicationId: Long,
    val roomId: UUID,
    val note: String,
    val resume: SubmittedResumeResponse,
    val status: String,
    val statusLabel: String,
    @get:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val appliedAt: LocalDateTime,
) {
    companion object {
        fun from(application: RoomApplication): MyRoomApplicationResponse {
            return MyRoomApplicationResponse(
                applicationId = application.id,
                roomId = application.roomId,
                note = application.note,
                resume = SubmittedResumeResponse(
                    resumeId = application.resumeSubmission.sourceResumeId,
                    file = ResumeFileResponse(
                        originalName = application.resumeSubmission.file.originalName,
                        sizeBytes = application.resumeSubmission.file.sizeBytes,
                        contentType = application.resumeSubmission.file.contentType,
                    ),
                    aiSummary = ResumeAiSummaryResponse(
                        status = application.resumeSummary.status.toResponseStatus(),
                        text = application.resumeSummary.content,
                    ),
                ),
                status = application.status.name,
                statusLabel = application.status.toApplicantStatusLabel(),
                appliedAt = application.appliedAt,
            )
        }
    }
}

data class SubmittedResumeResponse(
    val resumeId: UUID,
    val file: ResumeFileResponse,
    val aiSummary: ResumeAiSummaryResponse,
)

private fun ResumeSummaryStatus.toResponseStatus(): ResumeAiSummaryStatus {
    return when (this) {
        ResumeSummaryStatus.PROCESSING -> ResumeAiSummaryStatus.PROCESSING
        ResumeSummaryStatus.DONE -> ResumeAiSummaryStatus.DONE
        ResumeSummaryStatus.FAILED -> ResumeAiSummaryStatus.FAILED
    }
}
