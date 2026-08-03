package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 수락·반려는 각 매니저 호출이 자체 트랜잭션을 가지므로 클래스 레벨 @Transactional 없이 트랜잭션 밖에서 호출한다.
class RoomApplicationManagerIT(
    val roomApplicationManager: RoomApplicationManager,
    val roomRepository: RoomRepository,
    val roomApplicationRepository: RoomApplicationRepository,
    val participationRepository: ParticipationRepository,
) : ContextTest() {
    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val applicantId: UUID = UUID.randomUUID()
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    // 트랜잭션 롤백이 없으므로 이 테스트가 만든 행을 직접 지운다.
    @AfterEach
    fun cleanUp() {
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        roomRepository.deleteById(roomId)
    }

    @Test
    fun `수락하면 신청자가 참여자로 등록되고 신청이 ACCEPTED 로 바뀐다`() {
        // given — 정원 6, 방장 1명 참여, 대기 신청 1건
        seedRoom(maxCapacity = 6)
        seedHost()
        val applicationId = seedPendingApplication()

        // when
        val decision = roomApplicationManager.accept(roomId, applicationId, hostId)

        // then — 참여자 등록(+현재 인원 2), 신청 ACCEPTED, 대기 해제
        assertThat(decision.status).isEqualTo(RoomApplicationStatus.ACCEPTED)
        assertThat(decision.currentParticipants).isEqualTo(2)
        assertThat(decision.maxCapacity).isEqualTo(6)

        val application = roomApplicationRepository.findById(applicationId).orElseThrow()
        assertThat(application.status).isEqualTo(RoomApplicationStatus.ACCEPTED)
        assertThat(application.pendingMemberId).isNull()
        assertThat(application.handlerMemberId).isEqualTo(hostId)

        val participants = participationRepository.findAll().filter { it.roomId == roomId && it.isActive() }
        assertThat(participants).anyMatch { it.memberId == applicantId && it.participationRole == ParticipationRole.PARTICIPANT }
    }

    @Test
    fun `정원이 가득 차 있으면 수락은 ROOM_CAPACITY_FULL 로 거부된다`() {
        // given — 정원 2, 방장 + 참여자 1명으로 이미 가득 참, 대기 신청 1건
        seedRoom(maxCapacity = 2)
        seedHost()
        seedParticipant(UUID.randomUUID())
        val applicationId = seedPendingApplication()

        // when / then
        assertThatThrownBy { roomApplicationManager.accept(roomId, applicationId, hostId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_CAPACITY_FULL)
            }
        assertThat(roomApplicationRepository.findById(applicationId).orElseThrow().status)
            .isEqualTo(RoomApplicationStatus.PENDING)
    }

    @Test
    fun `방장이 아니면 수락은 ROOM_FORBIDDEN 으로 거부된다`() {
        seedRoom(maxCapacity = 6)
        seedHost()
        val applicationId = seedPendingApplication()

        assertThatThrownBy { roomApplicationManager.accept(roomId, applicationId, UUID.randomUUID()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_FORBIDDEN)
            }
    }

    @Test
    fun `이미 처리된 신청을 다시 수락하면 APPLICATION_ALREADY_HANDLED 로 거부된다`() {
        seedRoom(maxCapacity = 6)
        seedHost()
        val applicationId = seedPendingApplication()
        roomApplicationManager.accept(roomId, applicationId, hostId)

        assertThatThrownBy { roomApplicationManager.accept(roomId, applicationId, hostId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.APPLICATION_ALREADY_HANDLED)
            }
    }

    @Test
    fun `반려하면 신청이 REJECTED 로 바뀌고 정원과 참여자에는 영향이 없다`() {
        seedRoom(maxCapacity = 6)
        seedHost()
        val applicationId = seedPendingApplication()

        val decision = roomApplicationManager.reject(roomId, applicationId, hostId, "이번 방향과 맞지 않아요.")

        assertThat(decision.status).isEqualTo(RoomApplicationStatus.REJECTED)
        assertThat(decision.currentParticipants).isEqualTo(1) // 방장만
        val application = roomApplicationRepository.findById(applicationId).orElseThrow()
        assertThat(application.status).isEqualTo(RoomApplicationStatus.REJECTED)
        assertThat(application.rejectReason).isEqualTo("이번 방향과 맞지 않아요.")
        assertThat(application.pendingMemberId).isNull()
        // 참여자는 방장 1명 그대로
        assertThat(participationRepository.countByRoomIdAndDeletedAtIsNull(roomId)).isEqualTo(1)
    }

    private fun seedRoom(maxCapacity: Int) {
        roomRepository.save(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = maxCapacity.toShort(),
                startAt = now.plusDays(3),
                durationMinutes = 60,
            ),
        )
        // 상태 기본값은 RECRUITING 이라 별도 설정이 필요 없다.
        check(roomRepository.findById(roomId).orElseThrow().status == RoomStatus.RECRUITING)
    }

    private fun seedHost() {
        participationRepository.save(
            ParticipationEntity(
                roomId = roomId,
                memberId = hostId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
    }

    private fun seedParticipant(memberId: UUID) {
        participationRepository.save(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
    }

    private fun seedPendingApplication(): Long = roomApplicationRepository.save(
        RoomApplicationEntity(
            roomId = roomId,
            applicantMemberId = applicantId,
            note = "실전처럼 준비하고 싶어요.",
            appliedAt = now,
            status = RoomApplicationStatus.PENDING,
            pendingMemberId = applicantId,
        ),
    ).id
}
