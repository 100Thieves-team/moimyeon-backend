package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.domain.storage.ObjectStorageException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Profile("local-dev", "dev", "staging", "live")
@Service
class ResumeService(
    private val resumeFinder: ResumeFinder,
    private val fileStorage: ResumeFileStorage,
    private val resumeManager: ResumeManager,
    private val resumeRegistrar: ResumeRegistrar,
    private val documentSummarizer: DocumentSummarizer,
    private val clock: Clock,
) {
    fun get(memberId: UUID, resumeId: UUID): Resume {
        resumeManager.failExpiredSummaries(memberId, LocalDateTime.now(clock))
        return resumeFinder.get(memberId, resumeId)
    }

    fun getAll(memberId: UUID): List<Resume> {
        resumeManager.failExpiredSummaries(memberId, LocalDateTime.now(clock))
        return resumeFinder.getAll(memberId)
    }

    fun makeDefault(memberId: UUID, resumeId: UUID) {
        resumeManager.makeDefault(memberId, resumeId)
    }

    fun rename(memberId: UUID, resumeId: UUID, name: String) {
        resumeManager.rename(memberId, resumeId, name)
    }

    fun delete(memberId: UUID, resumeId: UUID) {
        resumeManager.delete(memberId, resumeId, LocalDateTime.now(clock))
    }

    fun register(memberId: UUID, upload: ResumeUpload): UUID {
        resumeRegistrar.validateCapacity(memberId)
        val newResume = fileStorage.store(memberId, upload).toNewResume()
        // TODO: DB에 참조되지 않은 업로드 객체를 주기적으로 찾아 삭제한다.
        val resumeId = resumeRegistrar.register(memberId, newResume)
        val summary = try {
            documentSummarizer.summarizePdf(upload.content)
        } catch (exception: DocumentSummarizationException) {
            resumeManager.failSummary(memberId, resumeId)
            return resumeId
        }
        resumeManager.completeSummary(memberId, resumeId, summary, LocalDateTime.now(clock))
        return resumeId
    }

    fun retrySummary(memberId: UUID, resumeId: UUID): UUID {
        val startedAt = LocalDateTime.now(clock)
        resumeManager.failExpiredSummaries(memberId, startedAt)
        val resume = resumeFinder.get(memberId, resumeId)
        resumeManager.startSummaryRetry(memberId, resumeId, startedAt)
        val summary = try {
            val content = fileStorage.read(resume.file)
            documentSummarizer.summarizePdf(content)
        } catch (exception: DocumentSummarizationException) {
            resumeManager.failSummary(memberId, resumeId)
            return resumeId
        } catch (exception: ObjectStorageException) {
            resumeManager.failSummary(memberId, resumeId)
            return resumeId
        }
        resumeManager.completeSummary(memberId, resumeId, summary, LocalDateTime.now(clock))
        return resumeId
    }
}
