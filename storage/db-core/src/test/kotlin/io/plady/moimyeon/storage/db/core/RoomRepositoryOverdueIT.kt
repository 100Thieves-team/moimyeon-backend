package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 8시간 자동 종료 배치(MOI-471)의 후보 조회. 기준 시각은 room_status_log 의
// IN_PROGRESS 전이 occurred_at 이다 — start_at(예정)이 아니다.
class RoomRepositoryOverdueIT(
    private val roomRepository: RoomRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) : CoreDbContextTest() {
    private val now = LocalDateTime.of(2026, 8, 17, 12, 0)
    private val threshold = now.minusHours(8)
    private val seededRoomIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId in seededRoomIds })
        roomRepository.deleteAllById(seededRoomIds)
    }

    @Test
    fun `기준 시각 이전에 시작한 진행 중 룸만 골라낸다`() {
        val overdue = seedRoom(status = RoomStatus.IN_PROGRESS, startLogAt = now.minusHours(9))
        seedRoom(status = RoomStatus.IN_PROGRESS, startLogAt = now.minusHours(7))
        seedRoom(status = RoomStatus.COMPLETED, startLogAt = now.minusHours(9))

        val found = roomRepository.findInProgressStartedBefore(threshold)

        assertThat(found.map { it.id }.filter { it in seededRoomIds }).containsExactly(overdue)
    }

    @Test
    fun `운영이 걷어낸 시작 전이 로그는 기준으로 삼지 않는다`() {
        val roomId = seedRoom(status = RoomStatus.IN_PROGRESS, startLogAt = now.minusHours(9))
        roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.IN_PROGRESS)
            ?.let { log ->
                log.delete(now)
                roomStatusLogRepository.saveAndFlush(log)
            } ?: error("시드한 시작 로그가 없음")

        val found = roomRepository.findInProgressStartedBefore(threshold)

        assertThat(found.map { it.id }).doesNotContain(roomId)
    }

    private fun seedRoom(status: RoomStatus, startLogAt: LocalDateTime): UUID {
        val roomId = UUID.randomUUID()
        seededRoomIds += roomId
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "자동 종료 후보 조회 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = 2,
            maxCapacity = 4,
            startAt = startLogAt,
            durationMinutes = 60,
        )
        room.confirm()
        room.startProgress(startLogAt)
        if (status == RoomStatus.COMPLETED) room.complete()
        roomRepository.saveAndFlush(room)
        roomStatusLogRepository.saveAndFlush(
            RoomStatusLogEntity.byMember(
                roomId = roomId,
                transitionType = RoomStatus.IN_PROGRESS,
                handlerMemberId = UUID.randomUUID(),
                occurredAt = startLogAt,
            ),
        )
        return roomId
    }
}
