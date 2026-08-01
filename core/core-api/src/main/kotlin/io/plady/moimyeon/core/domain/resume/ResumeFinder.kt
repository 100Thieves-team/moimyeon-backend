package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ResumeFinder(
    private val resumeRepository: ResumeRepository,
) {
    fun getVault(memberId: UUID): ResumeVault {
        val resumes = resumeRepository
            .findByMemberIdAndArchivedAtIsNullAndDeletedAtIsNullOrderByCreatedAtDesc(memberId)
            .map(ResumeMapper::toDomain)
        return ResumeVault(resumes)
    }
}
