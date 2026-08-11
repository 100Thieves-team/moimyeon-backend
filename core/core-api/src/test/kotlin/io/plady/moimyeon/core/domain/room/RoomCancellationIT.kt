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
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 세 개의 쓰기(상태 전이·이력·대기 신청 벌크)가 한 커밋인지 본다.
// 테스트에 @Transactional 을 두지 않는다 — 바깥 트랜잭션이 있으면 운영 코드의 프록시 경계와
// 커밋 시점이 가려져 원자성을 확인할 수 없다(testing.md 트랜잭션 절).
class RoomCancellationIT(
    private val roomManager: RoomManager,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val startAt = LocalDateTime.of(2026, 9, 1, 19, 0)

    @AfterEach
    fun cleanUp() {
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId == roomId })
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
    }

    @Test
    fun `룸 취소는 상태 전이와 신청 종료와 이력 기록을 한 커밋으로 남긴다`() {
        seedRecruitingRoom()
        repeat(3) { seedPendingApplication() }

        roomManager.cancel(roomId, hostMemberId)

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.CANCELED)
        assertThat(applications().map { it.status }).containsOnly(RoomApplicationStatus.ROOM_CANCELED)
        assertThat(applications().map { it.pendingMemberId }).containsOnlyNulls()

        val log = roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.CANCELED)
        assertThat(log).isNotNull()
        assertThat(log!!.handlerMemberId).isEqualTo(hostMemberId)
    }

    @Test
    fun `취소가 거부되면 상태도 신청도 이력도 그대로다`() {
        seedRecruitingRoom()
        seedPendingApplication()
        seedParticipant()

        assertThatThrownBy { roomManager.cancel(roomId, hostMemberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_HAS_PARTICIPANTS)
            }

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.RECRUITING)
        assertThat(applications().map { it.status }).containsOnly(RoomApplicationStatus.PENDING)
        assertThat(roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.CANCELED))
            .isNull()
    }

    private fun applications() = roomApplicationRepository.findAll().filter { it.roomId == roomId }

    private fun seedRecruitingRoom() {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "룸 취소 원자성 테스트 룸",
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
                joinedAt = startAt.minusDays(3),
            ),
        )
    }

    private fun seedParticipant() {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = UUID.randomUUID(),
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = startAt.minusDays(2),
            ),
        )
    }

    private fun seedPendingApplication() {
        val applicantMemberId = UUID.randomUUID()
        roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = applicantMemberId,
                note = "참여하고 싶습니다",
                appliedAt = startAt.minusDays(1),
                status = RoomApplicationStatus.PENDING,
                pendingMemberId = applicantMemberId,
            ),
        )
    }
}
