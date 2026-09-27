package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoomStatusLogHandlerType
import io.plady.moimyeon.core.event.OutboxEventSerializer
import io.plady.moimyeon.core.event.payload.RoomCompletedEventPayload
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.OutboxRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// 여러 스레드로 동시 실행하므로 발행 사실은 테스트 스레드 이벤트가 아니라 outbox 행으로 확인한다.
class RoomAutoCompleteIT(
    private val roomProgressManager: RoomProgressManager,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val attendanceRepository: AttendanceRepository,
    private val outboxRepository: OutboxRepository,
    private val outboxEventSerializer: OutboxEventSerializer,
) : ContextTest() {
    private val now = LocalDateTime.of(2026, 9, 24, 12, 0)
    private val seededRoomIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        outboxRepository.deleteAll(outboxRepository.findAll().filter { outbox -> seededRoomIds.any { it.toString() in outbox.payload } })
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId in seededRoomIds })
        attendanceRepository.deleteAll(attendanceRepository.findAll().filter { it.roomId in seededRoomIds })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId in seededRoomIds })
        roomRepository.deleteAllById(seededRoomIds)
        seededRoomIds.clear()
    }

    @Test
    fun `시작 8시간이 지난 확정 룸을 시스템 완료로 기록하고 확정 참여자를 담아 완료 사실을 발행한다`() {
        val overdue = seedConfirmedRoom(startAt = now.minusHours(9), participantCount = 2)
        val running = seedConfirmedRoom(startAt = now.minusHours(7), participantCount = 2)

        val overdueIds = roomProgressManager.findOverdueRoomIds(now).filter { it in seededRoomIds }
        overdueIds.forEach { roomProgressManager.completeOverdue(it, now) }

        assertThat(overdueIds).containsExactly(overdue)
        assertThat(roomStatus(overdue)).isEqualTo(RoomStatus.COMPLETED)
        assertThat(roomStatus(running)).isEqualTo(RoomStatus.CONFIRMED)
        val log = roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(overdue, RoomStatus.COMPLETED)
            ?: error("COMPLETED 전이 로그가 남지 않음")
        assertThat(log.handlerType).isEqualTo(RoomStatusLogHandlerType.SYSTEM)
        assertThat(log.handlerMemberId).isNull()
        val completed = completedFacts(overdue).single()
        assertThat(completed.completedByMemberId).isNull()
        assertThat(completed.confirmedParticipantMemberIds).hasSize(2)
    }

    @Test
    fun `자동 완료는 확정 참여자 전원을 방장 기록으로 출석 처리하고 완료 사실에 담는다`() {
        val roomId = seedConfirmedRoom(startAt = now.minusHours(9), participantCount = 2)
        val hostId = participationRepository.findAll().single { it.roomId == roomId && it.participationRole == ParticipationRole.HOST }.memberId

        roomProgressManager.completeOverdue(roomId, now)

        val attendances = attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)
        assertThat(attendances).hasSize(2)
        assertThat(attendances.map { it.status }).containsOnly(AttendanceStatus.ATTENDED)
        assertThat(attendances.map { it.recorderMemberId }).containsOnly(hostId)
        assertThat(completedFacts(roomId).single().attendedMemberIds).containsExactlyInAnyOrderElementsOf(attendances.map { it.memberId })
    }

    @Test
    fun `정확히 8시간 경계부터 자동 완료 후보에 포함한다`() {
        val boundary = seedConfirmedRoom(now.minusHours(8))
        seedConfirmedRoom(now.minusHours(8).plusNanos(1_000))

        assertThat(roomProgressManager.findOverdueRoomIds(now).filter { it in seededRoomIds }).containsExactly(boundary)
    }

    @Test
    fun `동시에 자동 완료해도 완료 로그와 완료 사실은 한 번만 남는다`() {
        val roomId = seedConfirmedRoom(now.minusHours(9), participantCount = 2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val calls = (1..2).map {
                pool.submit<Boolean> {
                    check(start.await(5, TimeUnit.SECONDS))
                    roomProgressManager.completeOverdue(roomId, now)
                }
            }
            start.countDown()

            assertThat(calls.map { it.get(10, TimeUnit.SECONDS) }).containsExactlyInAnyOrder(true, false)
            assertThat(
                roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED),
            ).isEqualTo(1)
            assertThat(completedFacts(roomId)).hasSize(1)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `방장 완료와 자동 완료가 겹쳐도 한쪽만 완료하고 출석은 이긴 쪽 것만 남는다`() {
        val roomId = seedConfirmedRoom(now.minusHours(9), participantCount = 2)
        val members = participationRepository.findAll().filter { it.roomId == roomId }
        val hostId = members.single { it.participationRole == ParticipationRole.HOST }.memberId
        val manual = members.map {
            Attendance(it.memberId, if (it.memberId == hostId) AttendanceStatus.ATTENDED else AttendanceStatus.ABSENT)
        }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val manualCall = pool.submit<Boolean> {
                check(start.await(5, TimeUnit.SECONDS))
                runCatching { roomProgressManager.complete(RoomProgressCompletionCommand(roomId, hostId, manual, now)) }.isSuccess
            }
            val autoCall = pool.submit<Boolean> {
                check(start.await(5, TimeUnit.SECONDS))
                roomProgressManager.completeOverdue(roomId, now)
            }
            start.countDown()

            val manualWon = manualCall.get(10, TimeUnit.SECONDS)
            val autoWon = autoCall.get(10, TimeUnit.SECONDS)
            assertThat(listOf(manualWon, autoWon)).containsExactlyInAnyOrder(true, false)
            assertThat(
                roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED),
            ).isEqualTo(1)
            assertThat(completedFacts(roomId)).hasSize(1)
            val recorded = attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)
                .map { Attendance(it.memberId, it.status) }
            val expected = if (manualWon) manual else members.map { Attendance(it.memberId, AttendanceStatus.ATTENDED) }
            assertThat(recorded).containsExactlyInAnyOrderElementsOf(expected)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun roomStatus(roomId: UUID): RoomStatus = roomRepository.findById(roomId).orElseThrow().status

    private fun completedFacts(roomId: UUID): List<RoomCompletedEventPayload> = facts<RoomCompletedEventPayload>(EventType.ROOM_COMPLETED).filter { it.roomId == roomId }

    private inline fun <reified T> facts(type: EventType): List<T> = outboxRepository.findAll()
        .filter { it.eventType == type.name }
        .map { outboxEventSerializer.deserialize(it.payload).payload }
        .filterIsInstance<T>()

    private fun seedConfirmedRoom(startAt: LocalDateTime, participantCount: Int = 1): UUID {
        val roomId = UUID.randomUUID()
        seededRoomIds += roomId
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "자동 완료 테스트 룸",
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
