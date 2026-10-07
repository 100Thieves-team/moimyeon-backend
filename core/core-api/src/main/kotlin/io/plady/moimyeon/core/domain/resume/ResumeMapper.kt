package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.storage.db.core.ResumeEntity

object ResumeMapper {
    fun toDomain(entity: ResumeEntity): Resume {
        return Resume(
            id = entity.id,
            name = entity.name,
            file = ResumeFile(
                key = entity.fileKey,
                originalName = entity.originalName,
                sizeBytes = entity.sizeBytes,
                contentType = entity.contentType,
            ),
            summary = toSummary(entity),
            isDefault = entity.isDefault,
            registeredAt = entity.createdAt,
        )
    }

    fun toSummary(entity: ResumeEntity): ResumeSummary {
        return ResumeSummary(
            status = entity.summaryStatus,
            content = entity.summaryContent,
        )
    }
}
