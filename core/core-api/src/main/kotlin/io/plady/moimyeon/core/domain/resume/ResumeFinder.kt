package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ResumeFinder(
    private val resumeRepository: ResumeRepository,
) {
    fun getAll(memberId: UUID): List<Resume> {
        return resumeRepository
            .findByMemberIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(memberId)
            .map(ResumeMapper::toDomain)
    }
}
