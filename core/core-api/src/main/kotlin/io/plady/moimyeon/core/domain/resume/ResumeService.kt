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
    fun getAll(memberId: UUID): List<Resume> = resumeFinder.getAll(memberId)

    fun makeDefault(memberId: UUID, resumeId: UUID) {
        resumeManager.makeDefault(memberId, resumeId)
    }

    fun rename(memberId: UUID, resumeId: UUID, name: String) {
        resumeManager.rename(memberId, resumeId, name)
    }

    fun delete(memberId: UUID, resumeId: UUID) {
        resumeManager.delete(memberId, resumeId, LocalDateTime.now())
    }

    fun register(memberId: UUID, upload: ResumeUpload): UUID {
        resumeRegistrar.validateCapacity(memberId)
        val newResume = fileStorage.store(memberId, upload).toNewResume()
        val resumeId = resumeRegistrar.register(memberId, newResume)
        resumeSummarizer.summarize(resumeId)
        return resumeId
    }
}
