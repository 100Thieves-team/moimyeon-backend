package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ResumeFinder(
    private val resumeRepository: ResumeRepository,
) {
    fun get(memberId: UUID, resumeId: UUID): Resume {
        val entity = requireFound(
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resumeId, memberId),
            CoreErrorType.RESUME_NOT_FOUND,
        )
        return ResumeMapper.toDomain(entity)
    }

    fun getAll(memberId: UUID): List<Resume> {
        return resumeRepository
            .findByMemberIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(memberId)
            .map(ResumeMapper::toDomain)
    }

    fun getSummary(memberId: UUID, resumeId: UUID): ResumeSummary {
        val entity = requireFound(
            resumeRepository.findByIdAndMemberId(resumeId, memberId),
            CoreErrorType.RESUME_NOT_FOUND,
        )
        return ResumeMapper.toSummary(entity)
    }
}
