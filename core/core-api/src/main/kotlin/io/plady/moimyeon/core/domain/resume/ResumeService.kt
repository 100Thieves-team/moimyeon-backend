package io.plady.moimyeon.core.domain.resume

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Profile("local-dev", "dev", "staging", "live")
@Service
class ResumeService(
    private val resumeFinder: ResumeFinder,
    private val storedResumeReader: StoredResumeReader,
    private val fileStorage: ResumeFileStore,
    private val resumeManager: ResumeManager,
    private val resumeRegistrar: ResumeRegistrar,
    private val summaryGenerator: ResumeSummaryGenerator,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(memberId: UUID, resumeId: UUID): Resume {
        resumeManager.failExpiredSummaries(memberId, now())
        return resumeFinder.get(memberId, resumeId)
    }

    fun getAll(memberId: UUID): List<Resume> {
        resumeManager.failExpiredSummaries(memberId, now())
        return resumeFinder.getAll(memberId)
    }

    fun getStored(memberId: UUID): List<StoredResume> {
        resumeManager.failExpiredSummaries(memberId, now())
        return storedResumeReader.getAll(memberId)
    }

    fun makeDefault(memberId: UUID, resumeId: UUID) {
        resumeManager.makeDefault(memberId, resumeId)
    }

    fun rename(memberId: UUID, resumeId: UUID, name: String) {
        resumeManager.rename(memberId, resumeId, name)
    }

    fun delete(memberId: UUID, resumeId: UUID) {
        resumeManager.delete(memberId, resumeId, now())
    }

    fun register(memberId: UUID, upload: ResumeUpload): UUID {
        resumeRegistrar.validateCapacity(memberId)
        val newResume = fileStorage.store(memberId, upload).toNewResume()
        // TODO: DB에 참조되지 않은 업로드 객체를 주기적으로 찾아 삭제한다.
        val attemptStartedAt = now()
        val resumeId = resumeRegistrar.register(memberId, newResume, attemptStartedAt)
        val summary = try {
            summaryGenerator.generate(upload.content)
        } catch (exception: ResumeSummaryGenerationException) {
            log.warn("Resume summarization failed: memberId={}, resumeId={}", memberId, resumeId, exception)
            resumeManager.failSummary(memberId, resumeId, attemptStartedAt)
            return resumeId
        }
        resumeManager.completeSummary(memberId, resumeId, summary, attemptStartedAt, now())
        return resumeId
    }

    fun retrySummary(memberId: UUID, resumeId: UUID): UUID {
        val attemptStartedAt = now()
        resumeManager.failExpiredSummaries(memberId, attemptStartedAt)
        val resume = resumeFinder.get(memberId, resumeId)
        resumeManager.startSummaryRetry(memberId, resumeId, attemptStartedAt)
        val summary = try {
            val content = fileStorage.read(resume.file)
            summaryGenerator.generate(content)
        } catch (exception: ResumeSummaryGenerationException) {
            log.warn("Resume summarization retry failed: memberId={}, resumeId={}", memberId, resumeId, exception)
            resumeManager.failSummary(memberId, resumeId, attemptStartedAt)
            return resumeId
        } catch (exception: ResumeFileStorageException) {
            log.warn("Resume source read failed: memberId={}, resumeId={}", memberId, resumeId, exception)
            resumeManager.failSummary(memberId, resumeId, attemptStartedAt)
            return resumeId
        }
        resumeManager.completeSummary(memberId, resumeId, summary, attemptStartedAt, now())
        return resumeId
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
