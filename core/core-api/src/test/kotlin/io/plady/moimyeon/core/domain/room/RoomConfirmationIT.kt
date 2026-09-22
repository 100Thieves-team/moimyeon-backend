package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

// 직접 완료의 쓰기(상태 전이·두 이력·전원 출석·대기 신청 벌크)가 한 커밋인지 본다.
// 테스트에 @Transactional 을 두지 않는다 — 바깥 트랜잭션이 있으면 운영 코드의 프록시 경계와
// 커밋 시점이 가려져 원자성을 확인할 수 없다(testing.md 트랜잭션 절).
class RoomConfirmationIT(
    private val roomManager: RoomManager,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val roomValidator: RoomValidator,
    private val roomApplicationManager: RoomApplicationManager,
    transactionManager: PlatformTransactionManager,
) : ContextTest() {
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()

    // 확정은 "일정이 지나지 않았을 것"을 보고 Clock 빈은 시스템 시계다. 값 자체가 아니라
    // "미래"라는 사실이 명세이므로 실제 현재 기준으로 잡는다.
    private val startAt: LocalDateTime = LocalDateTime.now().plusDays(7)

    @AfterEach
    fun cleanUp() {
        attendanceRepository.deleteAll(attendanceRepository.findAll().filter { it.roomId == roomId })
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId == roomId })
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
    }

    @Test
    fun `확정은 즉시 완료되고 두 이력과 전원 출석과 신청 종료를 한 커밋으로 남긴다`() {
        seedRoom()
        val participantMemberId = seedParticipant()
        val leftMemberId = seedLeftParticipant()
        repeat(2) { seedPendingApplication() }

        roomManager.confirm(roomId, hostMemberId)

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.COMPLETED)
        assertThat(applications().map { it.status }).containsOnly(RoomApplicationStatus.ROOM_CONFIRMED)
        assertThat(applications().map { it.pendingMemberId }).containsOnlyNulls()

        val confirmedLog = roomStatusLogRepository
            .findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.CONFIRMED)
        val completedLog = roomStatusLogRepository
            .findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED)
        val inProgressLog = roomStatusLogRepository
            .findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.IN_PROGRESS)
        assertThat(confirmedLog).isNotNull()
        assertThat(completedLog).isNotNull()
        assertThat(inProgressLog).isNull()
        assertThat(confirmedLog!!.handlerMemberId).isEqualTo(hostMemberId)
        assertThat(completedLog!!.handlerMemberId).isEqualTo(hostMemberId)
        assertThat(completedLog.occurredAt).isEqualTo(confirmedLog.occurredAt)

        val attendances = attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)
        assertThat(attendances.map { it.memberId }).containsExactlyInAnyOrder(hostMemberId, participantMemberId)
        assertThat(attendances.map { it.status }).containsOnly(AttendanceStatus.ATTENDED)
        assertThat(attendances.map { it.recorderMemberId }).containsOnly(hostMemberId)
        assertThat(attendances.map { it.recordedAt }).containsOnly(confirmedLog.occurredAt)
        assertThat(attendances.map { it.memberId }).doesNotContain(leftMemberId)

        assertThat(participationRepository.countAtRoomConfirmation(roomId, hostMemberId)).isEqualTo(1)
        assertThat(participationRepository.countAtRoomConfirmation(roomId, participantMemberId)).isEqualTo(1)
        assertThat(participationRepository.countAtRoomConfirmation(roomId, leftMemberId)).isZero()
    }

    // 방장 혼자라 최소 진행 인원(2)에 미달한다.
    @Test
    fun `확정이 거부되면 상태도 신청도 이력도 그대로다`() {
        seedRoom()
        seedPendingApplication()

        assertThatThrownBy { roomManager.confirm(roomId, hostMemberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_BELOW_MIN_CAPACITY)
            }

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.RECRUITING)
        assertThat(applications().map { it.status }).containsOnly(RoomApplicationStatus.PENDING)
        assertThat(roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.CONFIRMED))
            .isNull()
        assertThat(roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED))
            .isNull()
        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)).isEmpty()
    }

    // 출석 저장 실패가 앞선 상태 변경과 CONFIRMED 로그까지 함께 롤백되는지 본다.
    @Test
    fun `출석 저장이 실패하면 직접 완료의 모든 쓰기가 롤백된다`() {
        seedRoom()
        seedParticipant()
        seedPendingApplication()
        attendanceRepository.saveAndFlush(
            AttendanceEntity(
                roomId = roomId,
                memberId = hostMemberId,
                status = AttendanceStatus.ATTENDED,
                recorderMemberId = hostMemberId,
                recordedAt = startAt.minusDays(8),
            ),
        )

        assertThatThrownBy { roomManager.confirm(roomId, hostMemberId) }
            .isInstanceOf(DataIntegrityViolationException::class.java)

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.RECRUITING)
        assertThat(applications().map { it.status }).containsOnly(RoomApplicationStatus.PENDING)
        assertThat(roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.CONFIRMED))
            .isNull()
        assertThat(roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED))
            .isNull()
        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)).hasSize(1)
    }

    // 확정은 멱등하게 성공하지 않는다. 두 번째 요청은 거부되고 부작용도 한 번만 남는다.
    @Test
    fun `이미 완료된 룸에 다시 확정을 요청하면 E1410 이고 이력과 출석은 중복되지 않는다`() {
        seedRoom()
        seedParticipant()
        seedPendingApplication()

        roomManager.confirm(roomId, hostMemberId)

        assertThatThrownBy { roomManager.confirm(roomId, hostMemberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_NOT_RECRUITING)
            }
        assertThat(
            roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.CONFIRMED),
        ).isEqualTo(1)
        assertThat(
            roomStatusLogRepository.countByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.COMPLETED),
        ).isEqualTo(1)
        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId)).hasSize(2)
        assertThat(applications().map { it.status }).containsOnly(RoomApplicationStatus.ROOM_CONFIRMED)
    }

    // 직접 완료된 룸에서도 기존 확정 이후의 수정·신청·수락 게이트가 그대로 발효하는지 본다.
    @Test
    fun `확정된 룸은 수정할 수 없다`() {
        seedRoom()
        seedParticipant()
        roomManager.confirm(roomId, hostMemberId)

        assertThatThrownBy { roomManager.update(roomId, hostMemberId, updateCommand()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_NOT_EDITABLE)
            }
        assertThat(roomRepository.findById(roomId).orElseThrow().title).isEqualTo("진행 확정 원자성 테스트 룸")
    }

    @Test
    fun `확정된 룸에는 신청할 수 없다`() {
        seedRoom()
        seedParticipant()
        roomManager.confirm(roomId, hostMemberId)

        // 신청 제출 경로는 룸 행을 잠그므로 트랜잭션 안에서만 돈다. 운영과 같은 경계를 준다.
        assertThatThrownBy {
            transactionTemplate.executeWithoutResult { roomValidator.validateAcceptingApplications(roomId) }
        }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_NOT_RECRUITING)
            }
    }

    @Test
    fun `확정된 룸에서는 대기 신청을 수락할 수 없다`() {
        seedRoom()
        seedParticipant()
        val applicationId = seedPendingApplication()
        roomManager.confirm(roomId, hostMemberId)

        assertThatThrownBy { roomApplicationManager.accept(roomId, applicationId, hostMemberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_NOT_RECRUITING)
            }
    }

    private fun updateCommand() = RoomUpdateCommand(
        title = RoomTitle("확정 뒤에는 못 바꾸는 제목"),
        description = null,
        interviewStage = InterviewStage.FIRST,
        interviewType = InterviewType.JOB,
        meetingPlace = MeetingPlace.Online,
        capacity = RoomCapacity(min = 2, max = 4),
        schedule = RoomSchedule(startAt = startAt, durationMinutes = 60),
    )

    private fun applications() = roomApplicationRepository.findAll().filter { it.roomId == roomId }

    private fun seedRoom() {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "진행 확정 원자성 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 4,
                startAt = startAt,
                durationMinutes = 60,
            ),
        )
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = hostMemberId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = startAt.minusDays(10),
            ),
        )
    }

    private fun seedParticipant(): UUID {
        val memberId = UUID.randomUUID()
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = startAt.minusDays(9),
            ),
        )
        return memberId
    }

    private fun seedLeftParticipant(): UUID {
        val memberId = UUID.randomUUID()
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.LEFT,
                joinedAt = startAt.minusDays(8),
                leftByMemberId = memberId,
                leftAt = startAt.minusDays(7),
            ),
        )
        return memberId
    }

    private fun seedPendingApplication(): Long {
        val applicantMemberId = UUID.randomUUID()
        return roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = applicantMemberId,
                note = "참여하고 싶습니다",
                appliedAt = startAt.minusDays(1),
                status = RoomApplicationStatus.PENDING,
                pendingMemberId = applicantMemberId,
            ),
        ).id
    }
}
