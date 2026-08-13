package io.plady.moimyeon.core.domain.resume

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class StoredResumeReaderTest {
    private val resumeFinder = mockk<ResumeFinder>()
    private val resumeUseHistoryFinder = mockk<ResumeUseHistoryFinder>()
    private val reader = StoredResumeReader(resumeFinder, resumeUseHistoryFinder)

    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val roomId = UUID.fromString("00000000-0000-0000-0000-000000000101")

    @Test
    fun `최근 사용순으로 조회하되 저장된 기본 이력서 여부를 유지한다`() {
        val persistedDefault = resume("00000000-0000-0000-0000-000000000011", true, 1)
        val olderUsed = resume("00000000-0000-0000-0000-000000000012", false, 2)
        val latestUsed = resume("00000000-0000-0000-0000-000000000013", false, 3)
        val olderUse = lastUsed(10, "첫 번째 면접")
        val latestUse = lastUsed(12, "최근 면접")
        every { resumeFinder.getAll(memberId) } returns listOf(persistedDefault, latestUsed, olderUsed)
        every {
            resumeUseHistoryFinder.getLatest(memberId, listOf(persistedDefault.id, latestUsed.id, olderUsed.id))
        } returns mapOf(olderUsed.id to olderUse, latestUsed.id to latestUse)

        val result = reader.getAll(memberId)

        assertThat(result.map { it.resume.id }).containsExactly(latestUsed.id, olderUsed.id, persistedDefault.id)
        assertThat(result.map { it.isDefault }).containsExactly(false, false, true)
        assertThat(result.map { it.lastUsed }).containsExactly(latestUse, olderUse, null)
    }

    @Test
    fun `사용 이력이 전혀 없으면 기본 여부와 무관하게 등록 역순으로 조회한다`() {
        val persistedDefault = resume("00000000-0000-0000-0000-000000000021", true, 1)
        val newest = resume("00000000-0000-0000-0000-000000000022", false, 3)
        val older = resume("00000000-0000-0000-0000-000000000023", false, 2)
        every { resumeFinder.getAll(memberId) } returns listOf(older, persistedDefault, newest)
        every {
            resumeUseHistoryFinder.getLatest(memberId, listOf(older.id, persistedDefault.id, newest.id))
        } returns emptyMap()

        val result = reader.getAll(memberId)

        assertThat(result.map { it.resume.id }).containsExactly(newest.id, older.id, persistedDefault.id)
        assertThat(result.map { it.isDefault }).containsExactly(false, false, true)
        assertThat(result.map { it.lastUsed }).containsOnlyNulls()
    }

    @Test
    fun `사용 시각과 등록 시각이 같아도 이력서 식별자 역순으로 순서를 고정한다`() {
        val lowerId = resume("00000000-0000-0000-0000-000000000031", false, 1)
        val higherId = resume("00000000-0000-0000-0000-000000000032", true, 1)
        val sameUse = lastUsed(10, "동시 제출 면접")
        every { resumeFinder.getAll(memberId) } returns listOf(lowerId, higherId)
        every {
            resumeUseHistoryFinder.getLatest(memberId, listOf(lowerId.id, higherId.id))
        } returns mapOf(lowerId.id to sameUse, higherId.id to sameUse)

        val result = reader.getAll(memberId)

        assertThat(result.map { it.resume.id }).containsExactly(higherId.id, lowerId.id)
        assertThat(result.map { it.isDefault }).containsExactly(true, false)
    }

    @Test
    fun `저장한 이력서가 없으면 사용 이력을 조회하지 않는다`() {
        every { resumeFinder.getAll(memberId) } returns emptyList()

        assertThat(reader.getAll(memberId)).isEmpty()
        verify(exactly = 0) { resumeUseHistoryFinder.getLatest(any(), any()) }
    }

    private fun resume(id: String, isDefault: Boolean, registeredDay: Int): Resume = Resume(
        id = UUID.fromString(id),
        name = "$id.pdf",
        file = ResumeFile("resumes/$memberId/$id.pdf", "$id.pdf", 1_024, "application/pdf"),
        summary = ResumeSummary(ResumeSummaryStatus.DONE, "요약"),
        isDefault = isDefault,
        registeredAt = LocalDateTime.of(2026, 8, registeredDay, 12, 0),
    )

    private fun lastUsed(day: Int, roomTitle: String): ResumeLastUsed = ResumeLastUsed(
        roomId = roomId,
        roomTitle = roomTitle,
        usedAt = LocalDateTime.of(2026, 8, day, 12, 0),
    )
}
