package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.UUID

class RoomRepositoryOverdueIT(
    private val roomRepository: RoomRepository,
) : CoreDbContextTest() {
    private val now = LocalDateTime.of(2026, 8, 17, 12, 0)
    private val seeded = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        roomRepository.deleteAllById(seeded)
    }

    @Test
    fun `예정 시각이 8시간 이상 지난 확정 룸만 조회한다`() {
        val overdue = seed(RoomStatus.CONFIRMED, now.minusHours(9))
        val boundary = seed(RoomStatus.CONFIRMED, now.minusHours(8))
        seed(RoomStatus.CONFIRMED, now.minusHours(7))
        seed(RoomStatus.RECRUITING, now.minusHours(9))
        seed(RoomStatus.COMPLETED, now.minusHours(9))

        val found = roomRepository.findConfirmedStartedBefore(now.minusHours(8), Pageable.ofSize(100))

        assertThat(found.map { it.id }.filter { it in seeded }).containsExactly(overdue, boundary)
    }

    private fun seed(status: RoomStatus, startAt: LocalDateTime): UUID {
        val room = RoomEntity(
            id = UUID.randomUUID(),
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "자동 완료 후보 조회 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = 2,
            maxCapacity = 4,
            startAt = startAt,
            durationMinutes = 60,
        )
        when (status) {
            RoomStatus.RECRUITING -> Unit
            RoomStatus.CONFIRMED -> room.confirm()
            RoomStatus.COMPLETED -> {
                room.confirm()
                room.complete()
            }
            RoomStatus.CANCELED -> room.cancel()
        }
        roomRepository.saveAndFlush(room)
        seeded += room.id
        return room.id
    }
}
