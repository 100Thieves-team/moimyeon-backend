package io.plady.moimyeon.worker.room

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoomStatusLogHandlerType
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import io.plady.moimyeon.worker.WorkerContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RoomAutoCompleteIT(
    private val completer: OverdueRoomCompleter,
    private val job: RoomAutoCompleteJob,
    private val roomRepository: RoomRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) : WorkerContextTest() {
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

    @Test
    fun `WorkerApplication이 조립한 작업이 실제 룸을 종료한다`() {
        val current = LocalDateTime.now()
        val overdue = seedInProgressRoom(current.minusHours(9))
        val running = seedInProgressRoom(current.minusHours(7))

        job.run()

        assertThat(roomStatus(overdue)).isEqualTo(RoomStatus.COMPLETED)
        assertThat(roomStatus(running)).isEqualTo(RoomStatus.IN_PROGRESS)
    }

    @Test
    fun `정확히 8시간 경계부터 종료 후보에 포함한다`() {
        val boundary = seedInProgressRoom(now.minusHours(8))
        val beforeBoundary = seedInProgressRoom(now.minusHours(8).plusSeconds(1))

        assertThat(completer.findOverdueRoomIds(now).filter { it in seededRoomIds })
            .containsExactly(boundary)
        assertThat(roomStatus(beforeBoundary)).isEqualTo(RoomStatus.IN_PROGRESS)
    }

    @Test
    fun `동시 실행되어도 종료 로그는 하나만 남는다`() {
        val roomId = seedInProgressRoom(now.minusHours(9))
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val calls = (1..2).map {
                pool.submit<Boolean> {
                    check(start.await(5, TimeUnit.SECONDS))
                    completer.complete(roomId, now)
                }
            }
            start.countDown()
            assertThat(calls.map { it.get(10, TimeUnit.SECONDS) }).containsExactlyInAnyOrder(true, false)
            assertThat(roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED))
                .isEqualTo(1)
        } finally {
            pool.shutdownNow()
        }
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
