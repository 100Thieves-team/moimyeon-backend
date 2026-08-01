package io.plady.moimyeon.core.domain.resume

import java.time.LocalDateTime
import java.util.UUID

class ResumeService(
    private val resumeFinder: ResumeFinder,
    private val fileStorage: ResumeFileStorage,
    private val resumeManager: ResumeManager,
    private val resumeRegistrar: ResumeRegistrar,
    private val resumeSummarizer: ResumeSummarizer,
) {
    fun getVault(memberId: UUID): ResumeVault = resumeFinder.getVault(memberId)

    fun makeDefault(memberId: UUID, resumeId: UUID) {
        resumeManager.makeDefault(memberId, resumeId)
    }

    fun hide(memberId: UUID, resumeId: UUID) {
        resumeManager.hide(memberId, resumeId, LocalDateTime.now())
    }

    fun register(memberId: UUID, registration: ResumeRegistration): UUID {
        resumeRegistrar.validateCapacity(memberId)
        val file = fileStorage.store(memberId, registration.upload)
        val resumeId = resumeRegistrar.register(memberId, registration.name, file)
        resumeSummarizer.summarize(resumeId)
        return resumeId
    }
}
