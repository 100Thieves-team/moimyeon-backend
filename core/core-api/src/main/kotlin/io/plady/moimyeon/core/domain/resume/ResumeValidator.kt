package io.plady.moimyeon.core.domain.resume

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class ResumeValidator(
    private val resumeRepository: ResumeRepository,
) {
    fun validateOwnedBy(memberId: UUID, resumeId: UUID): ResumeFile {
        log.debug { "resume.validator.validateOwnedBy memberId=$memberId resumeId=$resumeId" }
        val resume = requireFound(
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resumeId, memberId),
            CoreErrorType.RESUME_NOT_FOUND,
        )
        return ResumeFile(
            key = resume.fileKey,
            originalName = resume.originalName,
            sizeBytes = resume.sizeBytes,
            contentType = resume.contentType,
        )
    }
}
