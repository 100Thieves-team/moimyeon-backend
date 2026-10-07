package io.plady.moimyeon.core.domain.resume

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

private val log = KotlinLogging.logger {}

@Profile("local-dev", "dev", "staging", "live")
@Service
class ResumeService(
    private val resumeFinder: ResumeFinder,
    private val storedResumeReader: StoredResumeReader,
    private val fileStorage: ResumeFileStore,
    private val resumeManager: ResumeManager,
    private val resumeRegistrar: ResumeRegistrar,
    private val summaryGenerator: ResumeSummaryGenerator,
    private val summaryTimeSource: ResumeSummaryTimeSource,
    private val clock: Clock,
) {

    fun get(memberId: UUID, resumeId: UUID): Resume {
        resumeManager.failExpiredSummaries(memberId, now())
        return resumeFinder.get(memberId, resumeId)
    }

    fun getStored(memberId: UUID): List<StoredResume> {
        resumeManager.failExpiredSummaries(memberId, now())
        return storedResumeReader.getAll(memberId)
    }

    fun makeDefault(memberId: UUID, resumeId: UUID) {
        log.debug { "resume.default.change memberId=$memberId resumeId=$resumeId" }
        resumeManager.makeDefault(memberId, resumeId)
    }

    fun rename(memberId: UUID, resumeId: UUID, name: String) {
        log.debug { "resume.rename memberId=$memberId resumeId=$resumeId" }
        resumeManager.rename(memberId, resumeId, name)
    }

    fun delete(memberId: UUID, resumeId: UUID) {
        log.debug { "resume.delete memberId=$memberId resumeId=$resumeId" }
        resumeManager.delete(memberId, resumeId, now())
    }

    fun register(memberId: UUID, upload: ResumeUpload): UUID {
        log.debug { "resume.register memberId=$memberId" }
        val summaryDeadline = ResumeSummaryDeadline.start(summaryTimeSource.nanoTime())
        resumeRegistrar.validateCapacity(memberId)
        val newResume = fileStorage.store(memberId, upload, summaryDeadline).toNewResume()
        // TODO: DB에 참조되지 않은 업로드 객체를 주기적으로 찾아 삭제한다.
        val attemptStartedAt = now()
        val resumeId = resumeRegistrar.register(memberId, newResume, attemptStartedAt)
        val summary = try {
            summaryGenerator.generate(upload.content, summaryDeadline)
        } catch (exception: ResumeSummaryGenerationException) {
            log.warn(exception) { "resume.summary.generation.failed memberId=$memberId resumeId=$resumeId" }
            resumeManager.failSummary(memberId, resumeId, attemptStartedAt)
            return resumeId
        }
        resumeManager.completeSummary(memberId, resumeId, summary, attemptStartedAt, now())
        return resumeId
    }

    fun retrySummary(memberId: UUID, resumeId: UUID): UUID {
        log.debug { "resume.summary.retry memberId=$memberId resumeId=$resumeId" }
        val summaryDeadline = ResumeSummaryDeadline.start(summaryTimeSource.nanoTime())
        val attemptStartedAt = now()
        resumeManager.failExpiredSummaries(memberId, attemptStartedAt)
        val resume = resumeFinder.get(memberId, resumeId)
        resumeManager.startSummaryRetry(memberId, resumeId, attemptStartedAt)
        val summary = try {
            val content = fileStorage.read(resume.file, summaryDeadline)
            summaryGenerator.generate(content, summaryDeadline)
        } catch (exception: ResumeSummaryGenerationException) {
            log.warn(exception) { "resume.summary.retry.failed memberId=$memberId resumeId=$resumeId" }
            resumeManager.failSummary(memberId, resumeId, attemptStartedAt)
            return resumeId
        } catch (exception: ResumeFileStorageException) {
            log.warn(exception) { "resume.source.read.failed memberId=$memberId resumeId=$resumeId" }
            resumeManager.failSummary(memberId, resumeId, attemptStartedAt)
            return resumeId
        }
        resumeManager.completeSummary(memberId, resumeId, summary, attemptStartedAt, now())
        return resumeId
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
