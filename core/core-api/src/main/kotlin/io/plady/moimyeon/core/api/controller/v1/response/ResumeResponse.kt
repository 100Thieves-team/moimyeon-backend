package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.resume.Resume
import io.plady.moimyeon.core.domain.resume.ResumeLastUsed
import io.plady.moimyeon.core.domain.resume.StoredResume
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import java.time.LocalDateTime
import java.util.UUID

data class ResumesResponse(
    val maxCount: Int,
    val resumes: List<StoredResumeResponse>,
) {
    companion object {
        fun from(resumes: List<StoredResume>): ResumesResponse {
            return ResumesResponse(MAX_RESUME_COUNT, resumes.map(StoredResumeResponse::from))
        }
    }
}

data class StoredResumeResponse(
    val resumeId: UUID,
    val name: String,
    val file: ResumeFileResponse,
    val aiSummary: ResumeAiSummaryResponse,
    val isDefault: Boolean,
    val registeredAt: LocalDateTime,
    val lastUsed: ResumeLastUsedResponse?,
) {
    companion object {
        fun from(storedResume: StoredResume): StoredResumeResponse {
            val resume = storedResume.resume
            return StoredResumeResponse(
                resumeId = resume.id,
                name = resume.name,
                file = ResumeFileResponse(
                    originalName = resume.file.originalName,
                    sizeBytes = resume.file.sizeBytes,
                    contentType = resume.file.contentType,
                ),
                aiSummary = ResumeAiSummaryResponse(
                    status = resume.summary.status.toResponseStatus(),
                    text = resume.summary.content,
                ),
                isDefault = storedResume.isDefault,
                registeredAt = resume.registeredAt,
                lastUsed = storedResume.lastUsed?.let(ResumeLastUsedResponse::from),
            )
        }
    }
}

data class ResumeLastUsedResponse(
    val roomId: UUID,
    val roomTitle: String,
    val usedAt: LocalDateTime,
) {
    companion object {
        fun from(lastUsed: ResumeLastUsed): ResumeLastUsedResponse {
            return ResumeLastUsedResponse(
                roomId = lastUsed.roomId,
                roomTitle = lastUsed.roomTitle,
                usedAt = lastUsed.usedAt,
            )
        }
    }
}

data class ResumeResponse(
    val resumeId: UUID,
    val name: String,
    val file: ResumeFileResponse,
    val aiSummary: ResumeAiSummaryResponse,
    val isDefault: Boolean,
    val registeredAt: LocalDateTime,
) {
    companion object {
        fun from(resume: Resume): ResumeResponse {
            return ResumeResponse(
                resumeId = resume.id,
                name = resume.name,
                file = ResumeFileResponse(
                    originalName = resume.file.originalName,
                    sizeBytes = resume.file.sizeBytes,
                    contentType = resume.file.contentType,
                ),
                aiSummary = ResumeAiSummaryResponse(
                    status = resume.summary.status.toResponseStatus(),
                    text = resume.summary.content,
                ),
                isDefault = resume.isDefault,
                registeredAt = resume.registeredAt,
            )
        }
    }
}

internal fun ResumeSummaryStatus.toResponseStatus(): ResumeAiSummaryStatus {
    return when (this) {
        ResumeSummaryStatus.PROCESSING -> ResumeAiSummaryStatus.PROCESSING
        ResumeSummaryStatus.DONE -> ResumeAiSummaryStatus.DONE
        ResumeSummaryStatus.FAILED -> ResumeAiSummaryStatus.FAILED
    }
}

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

private const val MAX_RESUME_COUNT = 10
