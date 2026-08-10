package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RoomProgressPersistenceIT(
    private val progressService: RoomProgressService,
    private val progressManager: RoomProgressManager,
    private val progressReader: RoomProgressReader,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val entityManager: EntityManager,
    transactionManager: PlatformTransactionManager,
) : ContextTest() {
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val participantMemberId = UUID.randomUUID()
    private val startedAt = LocalDateTime.of(2026, 8, 11, 0, 0)

    @Test
    fun `진행 서비스는 애플리케이션 컴포넌트로 제공된다`() {
        assertThat(progressService).isNotNull()
    }

    @AfterEach
    fun cleanUp() {
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId == roomId })
        attendanceRepository.deleteAll(attendanceRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
    }

    @Test
    fun `확정 참여자가 방장을 불참으로 기록해도 기존 방장을 유지하며 진행을 시작한다`() {
        seedConfirmedRoomAndParticipants()
        val command = command(
            listOf(
                Attendance(hostMemberId, AttendanceStatus.ABSENT),
                Attendance(participantMemberId, AttendanceStatus.ATTENDED),
            ),
            startedByMemberId = participantMemberId,
        )

        val result = progressManager.start(command)

        assertThat(result.status).isEqualTo(RoomStatus.IN_PROGRESS)
        assertThat(result.hostMemberId).isEqualTo(hostMemberId)
        assertThat(result.attendances).containsExactlyElementsOf(command.attendances)
        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.IN_PROGRESS)

        val savedAttendances = attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)
        assertThat(savedAttendances.map { it.memberId }).containsExactly(hostMemberId, participantMemberId)
        assertThat(savedAttendances.map { it.status })
            .containsExactly(AttendanceStatus.ABSENT, AttendanceStatus.ATTENDED)
        assertThat(savedAttendances).allSatisfy {
            assertThat(it.recorderMemberId).isEqualTo(participantMemberId)
            assertThat(it.recordedAt).isEqualTo(startedAt)
            assertThat(it.changeReason).isNull()
        }

        val startLog = roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(
            roomId,
            RoomStatus.IN_PROGRESS,
        )!!
        assertThat(startLog.handlerMemberId).isEqualTo(participantMemberId)
        assertThat(startLog.occurredAt).isEqualTo(startedAt)
        assertThat(progressReader.getAttendance(roomId, participantMemberId))
            .isEqualTo(Attendance(participantMemberId, AttendanceStatus.ATTENDED))
    }

    @Test
    fun `진행 중인 룸을 다시 시작하면 최초 결과를 반환하지 않고 거부한다`() {
        seedConfirmedRoomAndParticipants()
        progressManager.start(
            command(
                listOf(
                    Attendance(hostMemberId, AttendanceStatus.ATTENDED),
                    Attendance(participantMemberId, AttendanceStatus.ABSENT),
                ),
            ),
        )

        assertThatThrownBy {
            progressManager.start(
                command(
                    listOf(
                        Attendance(hostMemberId, AttendanceStatus.ABSENT),
                        Attendance(participantMemberId, AttendanceStatus.ATTENDED),
                    ),
                ),
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE)
        }

        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId).map { it.status })
            .containsExactly(AttendanceStatus.ATTENDED, AttendanceStatus.ABSENT)
        assertThat(roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.IN_PROGRESS))
            .isEqualTo(1)
    }

    @Test
    fun `출석 대상이 확정 참여자 집합과 다르면 진행을 시작하지 않는다`() {
        seedConfirmedRoomAndParticipants()
        val outsiderMemberId = UUID.randomUUID()
        val invalidAttendances = listOf(
            listOf(
                Attendance(hostMemberId, AttendanceStatus.ATTENDED),
                Attendance(hostMemberId, AttendanceStatus.ABSENT),
            ),
            listOf(Attendance(hostMemberId, AttendanceStatus.ATTENDED)),
            listOf(
                Attendance(hostMemberId, AttendanceStatus.ATTENDED),
                Attendance(outsiderMemberId, AttendanceStatus.ABSENT),
            ),
        )

        invalidAttendances.forEach { attendances ->
            assertThatThrownBy { progressManager.start(command(attendances)) }
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH)
                }

            assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.CONFIRMED)
            assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)).isEmpty()
            assertThat(
                roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(
                    roomId,
                    RoomStatus.IN_PROGRESS,
                ),
            ).isZero()
        }
    }

    @Test
    fun `동시에 시작하면 한 요청만 성공하고 다른 요청은 시작 불가로 거부된다`() {
        seedConfirmedRoomAndParticipants()
        val commands = listOf(
            command(
                listOf(
                    Attendance(hostMemberId, AttendanceStatus.ATTENDED),
                    Attendance(participantMemberId, AttendanceStatus.ABSENT),
                ),
            ),
            command(
                listOf(
                    Attendance(hostMemberId, AttendanceStatus.ABSENT),
                    Attendance(participantMemberId, AttendanceStatus.ATTENDED),
                ),
                startedByMemberId = participantMemberId,
            ),
        )
        val ready = CountDownLatch(commands.size)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(commands.size)

        val futures = commands.map { command ->
            executor.submit<Result<RoomProgressStartResult>> {
                ready.countDown()
                start.await()
                runCatching { progressManager.start(command) }
            }
        }
        check(ready.await(3, TimeUnit.SECONDS))
        start.countDown()
        val results = futures.map { it.get(5, TimeUnit.SECONDS) }
        executor.shutdownNow()

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        val failure = results.single { it.isFailure }.exceptionOrNull()
        assertThat(failure).isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE)
        }
        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)).hasSize(2)
        assertThat(roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.IN_PROGRESS))
            .isEqualTo(1)
    }

    @Test
    fun `출석 기록이 없으면 조회하지 못한다`() {
        assertThatThrownBy { progressReader.getAttendance(roomId, participantMemberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_ATTENDANCE_NOT_FOUND)
            }
    }

    private fun command(
        attendances: List<Attendance>,
        startedByMemberId: UUID = hostMemberId,
    ): RoomProgressStartCommand {
        return RoomProgressStartCommand(
            roomId = roomId,
            startedByMemberId = startedByMemberId,
            attendances = attendances,
            startedAt = startedAt,
        )
    }

    private fun seedConfirmedRoomAndParticipants() {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "진행 저장 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 4,
                startAt = startedAt,
                durationMinutes = 60,
            ),
        )
        participationRepository.saveAllAndFlush(
            listOf(
                ParticipationEntity(
                    roomId = roomId,
                    memberId = hostMemberId,
                    participationRole = ParticipationRole.HOST,
                    status = ParticipationStatus.JOINED,
                    joinedAt = startedAt.minusDays(2),
                ),
                ParticipationEntity(
                    roomId = roomId,
                    memberId = participantMemberId,
                    participationRole = ParticipationRole.PARTICIPANT,
                    status = ParticipationStatus.JOINED,
                    joinedAt = startedAt.minusDays(1),
                ),
            ),
        )
        roomStatusLogRepository.saveAndFlush(
            RoomStatusLogEntity(
                roomId = roomId,
                transitionType = RoomStatus.CONFIRMED,
                handlerMemberId = hostMemberId,
                occurredAt = startedAt.minusHours(1),
            ),
        )
        transactionTemplate.executeWithoutResult {
            entityManager.createNativeQuery("update room set status = 'CONFIRMED' where id = :roomId")
                .setParameter("roomId", roomId)
                .executeUpdate()
            entityManager.clear()
        }
    }
}
