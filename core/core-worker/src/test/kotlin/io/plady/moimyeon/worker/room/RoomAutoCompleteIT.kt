package io.plady.moimyeon.worker.room

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoomStatusLogHandlerType
import io.plady.moimyeon.storage.db.core.OutboxRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
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
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val outboxRepository: OutboxRepository,
) : WorkerContextTest() {
    private val now = LocalDateTime.of(2026, 9, 24, 12, 0)
    private val seededRoomIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        outboxRepository.deleteAll(
            outboxRepository.findAll().filter { outbox -> seededRoomIds.any { it.toString() in outbox.payload } },
        )
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId in seededRoomIds })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId in seededRoomIds })
        roomRepository.deleteAllById(seededRoomIds)
        seededRoomIds.clear()
    }

    @Test
    fun `시작 8시간이 지난 확정 룸을 완료하고 로그와 수신자별 Outbox를 함께 남긴다`() {
        val overdue = seedConfirmedRoom(startAt = now.minusHours(9), participantCount = 2)
        val running = seedConfirmedRoom(startAt = now.minusHours(7), participantCount = 2)

        val overdueIds = completer.findOverdueRoomIds(now).filter { it in seededRoomIds }
        overdueIds.forEach { completer.complete(it, now) }

        assertThat(overdueIds).containsExactly(overdue)
        assertThat(roomStatus(overdue)).isEqualTo(RoomStatus.COMPLETED)
        assertThat(roomStatus(running)).isEqualTo(RoomStatus.CONFIRMED)
        val log = roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(
            overdue,
            RoomStatus.COMPLETED,
        ) ?: error("COMPLETED 전이 로그가 남지 않음")
        assertThat(log.handlerType).isEqualTo(RoomStatusLogHandlerType.SYSTEM)
        assertThat(log.handlerMemberId).isNull()
        assertThat(log.occurredAt).isEqualTo(now)
        assertThat(completionOutboxes(overdue)).hasSize(2)
    }

    @Test
    fun `정확히 8시간 경계부터 자동 완료 후보에 포함한다`() {
        val boundary = seedConfirmedRoom(now.minusHours(8))
        val beforeBoundary = seedConfirmedRoom(now.minusHours(8).plusNanos(1_000))

        assertThat(completer.findOverdueRoomIds(now).filter { it in seededRoomIds }).containsExactly(boundary)
        assertThat(roomStatus(beforeBoundary)).isEqualTo(RoomStatus.CONFIRMED)
    }

    @Test
    fun `동시에 자동 완료해도 완료 로그와 수신자별 Outbox는 한 번만 남는다`() {
        val roomId = seedConfirmedRoom(now.minusHours(9), participantCount = 2)
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
            assertThat(
                roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED),
            ).isEqualTo(1)
            assertThat(completionOutboxes(roomId)).hasSize(2)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun roomStatus(roomId: UUID): RoomStatus = roomRepository.findById(roomId).orElseThrow().status

    private fun completionOutboxes(roomId: UUID) = outboxRepository.findAll().filter {
        it.eventType == EventType.ROOM_COMPLETED && roomId.toString() in it.payload
    }

    private fun seedConfirmedRoom(startAt: LocalDateTime, participantCount: Int = 1): UUID {
        val roomId = UUID.randomUUID()
        seededRoomIds += roomId
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "자동 완료 배치 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = 1,
            maxCapacity = 4,
            startAt = startAt,
            durationMinutes = 60,
        )
        room.confirm()
        roomRepository.saveAndFlush(room)

        val confirmedAt = startAt.minusHours(1)
        roomStatusLogRepository.saveAndFlush(
            RoomStatusLogEntity.byMember(
                roomId = roomId,
                transitionType = RoomStatus.CONFIRMED,
                handlerMemberId = UUID.randomUUID(),
                occurredAt = confirmedAt,
            ),
        )
        participationRepository.saveAllAndFlush(
            (1..participantCount).map { index ->
                ParticipationEntity(
                    roomId = roomId,
                    memberId = UUID.randomUUID(),
                    participationRole = if (index == 1) ParticipationRole.HOST else ParticipationRole.PARTICIPANT,
                    status = ParticipationStatus.JOINED,
                    joinedAt = confirmedAt.minusMinutes(1),
                )
            },
        )
        return roomId
    }
}
