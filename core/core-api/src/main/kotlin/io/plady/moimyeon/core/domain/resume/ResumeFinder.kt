package io.plady.moimyeon.core.domain.resume

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class ResumeFinder(
    private val resumeRepository: ResumeRepository,
) {
    fun get(memberId: UUID, resumeId: UUID): Resume {
        log.debug { "resume.finder.get memberId=$memberId resumeId=$resumeId" }
        val entity = requireFound(
            resumeRepository.findByIdAndMemberIdAndDeletedAtIsNull(resumeId, memberId),
            CoreErrorType.RESUME_NOT_FOUND,
        )
        return ResumeMapper.toDomain(entity)
    }

    fun getAll(memberId: UUID): List<Resume> {
        log.debug { "resume.finder.getAll memberId=$memberId" }
        return resumeRepository
            .findByMemberIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtDesc(memberId)
            .map(ResumeMapper::toDomain)
    }

    fun getSummary(memberId: UUID, resumeId: UUID): ResumeSummary {
        log.debug { "resume.finder.getSummary memberId=$memberId resumeId=$resumeId" }
        val entity = requireFound(
            resumeRepository.findByIdAndMemberId(resumeId, memberId),
            CoreErrorType.RESUME_NOT_FOUND,
        )
        return ResumeMapper.toSummary(entity)
    }

    fun getSummaries(resumeIds: Collection<UUID>): Map<UUID, ResumeSummary> {
        log.debug { "resume.finder.getSummaries resumeIdsCount=${resumeIds.size}" }
        if (resumeIds.isEmpty()) return emptyMap()
        val entitiesById = resumeRepository.findByIdIn(resumeIds).associateBy { it.id }
        return resumeIds.distinct().associateWith { resumeId ->
            val entity = requireFound(entitiesById[resumeId], CoreErrorType.RESUME_NOT_FOUND)
            ResumeMapper.toSummary(entity)
        }
    }
}
