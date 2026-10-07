package io.plady.moimyeon.core.domain.resume

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class StoredResumeReader(
    private val resumeFinder: ResumeFinder,
    private val resumeUseHistoryFinder: ResumeUseHistoryFinder,
) {
    @Transactional(readOnly = true)
    fun getAll(memberId: UUID): List<StoredResume> {
        log.debug { "stored-resume.reader.getAll memberId=$memberId" }
        val resumes = resumeFinder.getAll(memberId)
        if (resumes.isEmpty()) return emptyList()

        val lastUsedByResumeId = resumeUseHistoryFinder.getLatest(memberId, resumes.map { it.id })

        return resumes
            .map { resume ->
                StoredResume(
                    resume = resume,
                    lastUsed = lastUsedByResumeId[resume.id],
                    isDefault = resume.isDefault,
                )
            }
            .sortedWith(
                compareByDescending<StoredResume> { it.lastUsed != null }
                    .thenByDescending { it.lastUsed?.usedAt }
                    .thenByDescending { it.resume.registeredAt }
                    .thenByDescending { it.resume.id },
            )
    }
}
