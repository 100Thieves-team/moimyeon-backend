package io.plady.moimyeon.batch.room

import io.plady.moimyeon.batch.BatchContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoomStatusLogHandlerType
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomAutoCompleteIT(
    private val completer: OverdueRoomCompleter,
    private val roomRepository: RoomRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) : BatchContextTest() {
    private val now = LocalDateTime.of(2026, 8, 17, 12, 0)
    private val seededRoomIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId in seededRoomIds })
        roomRepository.deleteAllById(seededRoomIds)
    }

    @Test
    fun `시작 8시간이 지난 룸을 COMPLETED 로 전이하고 SYSTEM 로그를 남긴다`() {
        val overdue = seedInProgressRoom(startedAt = now.minusHours(9))
        val running = seedInProgressRoom(startedAt = now.minusHours(7))

        val overdueIds = completer.findOverdueRoomIds(now).filter { it in seededRoomIds }
        overdueIds.forEach { completer.complete(it, now) }

        assertThat(overdueIds).containsExactly(overdue)
        assertThat(roomStatus(overdue)).isEqualTo(RoomStatus.COMPLETED)
        assertThat(roomStatus(running)).isEqualTo(RoomStatus.IN_PROGRESS)
        val log = roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(
            overdue,
            RoomStatus.COMPLETED,
        ) ?: error("COMPLETED 전이 로그가 남지 않음")
        assertThat(log.handlerType).isEqualTo(RoomStatusLogHandlerType.SYSTEM)
        assertThat(log.handlerMemberId).isNull()
        assertThat(log.occurredAt).isEqualTo(now)
    }

    @Test
    fun `다시 실행해도 종료는 한 번만 처리된다`() {
        val overdue = seedInProgressRoom(startedAt = now.minusHours(9))

        completer.complete(overdue, now)
        val secondRunCandidates = completer.findOverdueRoomIds(now.plusMinutes(10)).filter { it in seededRoomIds }
        completer.complete(overdue, now.plusMinutes(10))

        assertThat(secondRunCandidates).isEmpty()
        assertThat(roomStatus(overdue)).isEqualTo(RoomStatus.COMPLETED)
        assertThat(
            roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(overdue, RoomStatus.COMPLETED),
        ).isEqualTo(1L)
    }

    private fun roomStatus(roomId: UUID): RoomStatus = roomRepository.findById(roomId).orElseThrow().status

    private fun seedInProgressRoom(startedAt: LocalDateTime): UUID {
        val roomId = UUID.randomUUID()
        seededRoomIds += roomId
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "자동 종료 배치 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = 2,
            maxCapacity = 4,
            startAt = startedAt,
            durationMinutes = 60,
        )
        room.confirm()
        room.startProgress(startedAt)
        roomRepository.saveAndFlush(room)
        roomStatusLogRepository.saveAndFlush(
            RoomStatusLogEntity.byMember(
                roomId = roomId,
                transitionType = RoomStatus.IN_PROGRESS,
                handlerMemberId = UUID.randomUUID(),
                occurredAt = startedAt,
            ),
        )
        return roomId
    }
}
