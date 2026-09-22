package io.plady.moimyeon.core.qa

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class QaResumeSummaryCompleterIT(
    private val qaResumeSummaryCompleter: QaResumeSummaryCompleter,
    private val resumeRepository: ResumeRepository,
) : ContextTest() {
    private val memberId = UUID.randomUUID()
    private val at = LocalDateTime.of(2026, 9, 22, 10, 0)
    private val seededIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        resumeRepository.deleteAllById(seededIds)
    }

    @Test
    fun `실패한 요약을 완료로 바꾸고 기본 이력서가 없으면 기본으로 지정한다`() {
        val resumeId = seedResume(ResumeSummaryStatus.FAILED, isDefault = false)

        val result = qaResumeSummaryCompleter.complete(resumeId, "[QA] 요약")

        assertThat(result.status).isEqualTo(ResumeSummaryStatus.DONE)
        assertThat(result.content).isEqualTo("[QA] 요약")
        assertThat(result.isDefault).isTrue()
        val entity = resumeRepository.findById(resumeId).orElseThrow()
        assertThat(entity.summaryStatus).isEqualTo(ResumeSummaryStatus.DONE)
        assertThat(entity.isDefault).isTrue()
    }

    @Test
    fun `이미 완료된 요약은 바꾸지 않고 기본 지정도 유지한다`() {
        val defaultId = seedResume(ResumeSummaryStatus.DONE, isDefault = true, content = "기존 요약")
        val otherId = seedResume(ResumeSummaryStatus.PROCESSING, isDefault = false)

        val kept = qaResumeSummaryCompleter.complete(defaultId, "새 요약")
        val completed = qaResumeSummaryCompleter.complete(otherId, "새 요약")

        assertThat(kept.content).isEqualTo("기존 요약")
        assertThat(completed.status).isEqualTo(ResumeSummaryStatus.DONE)
        assertThat(completed.isDefault).isFalse()
    }

    @Test
    fun `없거나 삭제된 이력서는 E1010 을 던진다`() {
        val deletedId = seedResume(ResumeSummaryStatus.FAILED, isDefault = false)
        resumeRepository.findById(deletedId).orElseThrow().let {
            it.delete(at)
            resumeRepository.saveAndFlush(it)
        }

        listOf(UUID.randomUUID(), deletedId).forEach { id ->
            assertThatThrownBy { qaResumeSummaryCompleter.complete(id, "요약") }
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_FOUND)
                }
        }
    }

    @Test
    fun `이름이 QA 마커로 시작하지 않는 이력서는 E2201 로 거절하고 바꾸지 않는다`() {
        val resumeId = seedResume(ResumeSummaryStatus.FAILED, isDefault = false, name = "실데이터 이력서.pdf")

        assertThatThrownBy { qaResumeSummaryCompleter.complete(resumeId, "요약") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }

        assertThat(resumeRepository.findById(resumeId).orElseThrow().summaryStatus).isEqualTo(ResumeSummaryStatus.FAILED)
    }

    private fun seedResume(
        status: ResumeSummaryStatus,
        isDefault: Boolean,
        content: String? = null,
        name: String = "[QA] resume.pdf",
    ): UUID {
        val id = UUID.randomUUID()
        resumeRepository.saveAndFlush(
            ResumeEntity(
                id = id,
                memberId = memberId,
                name = name,
                fileKey = "resumes/$id.pdf",
                originalName = "resume.pdf",
                sizeBytes = 1024,
                contentType = "application/pdf",
                summaryStatus = status,
                summaryContent = content,
                summaryStartedAt = at,
                isDefault = isDefault,
            ),
        )
        seededIds += id
        return id
    }
}
