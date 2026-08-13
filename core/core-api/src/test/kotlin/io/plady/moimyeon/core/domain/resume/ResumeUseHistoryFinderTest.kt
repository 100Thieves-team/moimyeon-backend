package io.plady.moimyeon.core.domain.resume

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.domain.room.RoomTitle
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ResumeUseHistoryFinderTest {
    private val resumeSubmissionRepository = mockk<ResumeSubmissionRepository>()
    private val roomFinder = mockk<RoomFinder>()
    private val finder = ResumeUseHistoryFinder(resumeSubmissionRepository, roomFinder)

    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val firstResumeId = UUID.fromString("00000000-0000-0000-0000-000000000011")
    private val secondResumeId = UUID.fromString("00000000-0000-0000-0000-000000000012")
    private val latestRoomId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val oldRoomId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val secondRoomId = UUID.fromString("00000000-0000-0000-0000-000000000103")

    @Test
    fun `이력서별 최신 성공 제출 한 건을 선택하고 룸 제목은 한 번에 조회한다`() {
        val latestAt = LocalDateTime.of(2026, 8, 12, 12, 0)
        val oldAt = LocalDateTime.of(2026, 8, 10, 12, 0)
        val secondAt = LocalDateTime.of(2026, 8, 11, 12, 0)
        val latest = submission(firstResumeId, latestRoomId, latestAt)
        val old = submission(firstResumeId, oldRoomId, oldAt)
        val second = submission(secondResumeId, secondRoomId, secondAt)
        every {
            resumeSubmissionRepository
                .findByMemberIdAndSourceResumeIdInAndDeletedAtIsNullOrderBySubmittedAtDescIdDesc(
                    memberId,
                    listOf(firstResumeId, secondResumeId),
                )
        } returns listOf(latest, second, old)
        every {
            roomFinder.getAllByIds(listOf(latestRoomId, secondRoomId))
        } returns listOf(
            room(latestRoomId, "최근 백엔드 기술 면접 스터디"),
            room(secondRoomId, "두 번째 백엔드 면접 스터디"),
        )

        val result = finder.getLatest(memberId, listOf(firstResumeId, secondResumeId))

        assertThat(result).containsExactlyEntriesOf(
            linkedMapOf(
                firstResumeId to ResumeLastUsed(latestRoomId, "최근 백엔드 기술 면접 스터디", latestAt),
                secondResumeId to ResumeLastUsed(secondRoomId, "두 번째 백엔드 면접 스터디", secondAt),
            ),
        )
        verify(exactly = 1) {
            resumeSubmissionRepository
                .findByMemberIdAndSourceResumeIdInAndDeletedAtIsNullOrderBySubmittedAtDescIdDesc(
                    memberId,
                    listOf(firstResumeId, secondResumeId),
                )
        }
        verify(exactly = 1) { roomFinder.getAllByIds(listOf(latestRoomId, secondRoomId)) }
    }

    @Test
    fun `조회할 이력서가 없으면 제출 이력과 룸을 조회하지 않는다`() {
        assertThat(finder.getLatest(memberId, emptyList())).isEmpty()

        verify(exactly = 0) { resumeSubmissionRepository.findByMemberIdAndSourceResumeIdInAndDeletedAtIsNullOrderBySubmittedAtDescIdDesc(any(), any()) }
        verify(exactly = 0) { roomFinder.getAllByIds(any()) }
    }

    @Test
    fun `최근 사용한 룸이 삭제되었으면 삭제된 면접으로 표시한다`() {
        val usedAt = LocalDateTime.of(2026, 8, 12, 12, 0)
        val submission = submission(firstResumeId, latestRoomId, usedAt)
        every {
            resumeSubmissionRepository
                .findByMemberIdAndSourceResumeIdInAndDeletedAtIsNullOrderBySubmittedAtDescIdDesc(
                    memberId,
                    listOf(firstResumeId),
                )
        } returns listOf(submission)
        every { roomFinder.getAllByIds(listOf(latestRoomId)) } returns emptyList()

        val result = finder.getLatest(memberId, listOf(firstResumeId))

        assertThat(result).containsEntry(
            firstResumeId,
            ResumeLastUsed(latestRoomId, "삭제된 면접", usedAt),
        )
    }

    private fun submission(
        resumeId: UUID,
        roomId: UUID,
        submittedAt: LocalDateTime,
    ): ResumeSubmissionEntity = mockk {
        every { sourceResumeId } returns resumeId
        every { this@mockk.roomId } returns roomId
        every { this@mockk.submittedAt } returns submittedAt
    }

    private fun room(id: UUID, title: String): Room = mockk {
        every { this@mockk.id } returns id
        every { this@mockk.title } returns RoomTitle(title)
    }
}
