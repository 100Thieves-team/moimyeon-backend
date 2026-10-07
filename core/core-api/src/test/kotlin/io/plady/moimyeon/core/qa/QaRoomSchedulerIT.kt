package io.plady.moimyeon.core.qa

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class QaRoomSchedulerIT(
    private val qaRoomScheduler: QaRoomScheduler,
    private val roomRepository: RoomRepository,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val at = LocalDateTime.of(2026, 9, 22, 10, 0)

    @AfterEach
    fun cleanUp() {
        roomRepository.deleteById(roomId)
    }

    @Test
    fun `확정된 QA 룸의 시작 시각을 과거로 옮기면 자동 완료 조건이 열린다`() {
        seedRoom("[QA] 일정 룸")
        roomRepository.findById(roomId).orElseThrow().let {
            it.confirm()
            roomRepository.saveAndFlush(it)
        }
        assertThat(roomRepository.findById(roomId).orElseThrow().isAutoCompletable(at)).isFalse()
        val past = at.minusDays(1)

        val schedule = qaRoomScheduler.reschedule(roomId, past)

        assertThat(schedule.startAt).isEqualTo(past)
        assertThat(schedule.status).isEqualTo(RoomStatus.CONFIRMED)
        val room = roomRepository.findById(roomId).orElseThrow()
        assertThat(room.startAt).isEqualTo(past)
        assertThat(room.status).isEqualTo(RoomStatus.CONFIRMED)
        assertThat(room.isAutoCompletable(at)).isTrue()
    }

    @Test
    fun `QA 마커가 없는 룸은 E2201 로 거절하고 시각을 바꾸지 않는다`() {
        seedRoom("실데이터 룸")

        assertThatThrownBy { qaRoomScheduler.reschedule(roomId, at.minusDays(1)) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }

        assertThat(roomRepository.findById(roomId).orElseThrow().startAt).isEqualTo(at.plusDays(7))
    }

    @Test
    fun `없는 룸은 E1405 를 던진다`() {
        assertThatThrownBy { qaRoomScheduler.reschedule(UUID.randomUUID(), at) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_NOT_FOUND)
            }
    }

    private fun seedRoom(title: String) {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                sigunguId = null,
                title = title,
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 6,
                startAt = at.plusDays(7),
                durationMinutes = 60,
            ),
        )
    }
}
