package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// 명부에는 §6 이 비공개로 지정한 것(AI 이력서 요약·제출 이력서 참조)이 실린다.
// 게이트가 "이 룸에 JOINED 참여 행이 있는가" 하나뿐이라 방장 전용 검증을 그대로 복사하기 쉽고,
// 그 실수는 코드만 봐서는 드러나지 않는다. 여기서 고정한다.
@Transactional
class RoomParticipantServiceIT(
    private val roomParticipantService: RoomParticipantService,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val participantMemberId = UUID.randomUUID()
    private val outsiderMemberId = UUID.randomUUID()
    private val leftMemberId = UUID.randomUUID()
    private val removedMemberId = UUID.randomUUID()

    private val startAt = LocalDateTime.of(2026, 9, 1, 19, 0)
    private val joinedAt = LocalDateTime.of(2026, 8, 1, 10, 0)

    @BeforeEach
    fun setUp() {
        persistRoom()
        persistParticipation(hostMemberId, ParticipationRole.HOST)
        persistParticipation(participantMemberId, ParticipationRole.PARTICIPANT)
    }

    @Test
    fun `방장은 명부를 조회한다`() {
        assertThatCode { roomParticipantService.getParticipants(hostMemberId, roomId) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `참여자는 명부를 조회한다`() {
        assertThatCode { roomParticipantService.getParticipants(participantMemberId, roomId) }
            .doesNotThrowAnyException()
    }

    // 신청자·반려자는 참여 행이 아예 없어 제3자와 데이터 모양이 같다. 따로 만들지 않는다.
    @Test
    fun `룸에 참여 중이 아닌 사람이 명부를 조회하면 E1419 를 던진다`() {
        assertForbidden(outsiderMemberId)

        persistParticipation(
            memberId = leftMemberId,
            role = ParticipationRole.PARTICIPANT,
            status = ParticipationStatus.LEFT,
            leftByMemberId = leftMemberId,
        )
        assertForbidden(leftMemberId)

        persistParticipation(
            memberId = removedMemberId,
            role = ParticipationRole.PARTICIPANT,
            status = ParticipationStatus.LEFT,
            leftByMemberId = hostMemberId,
        )
        assertForbidden(removedMemberId)
    }

    // 이미 속한 사람에게는 룸이 끝나도 진입점이 유지된다(「룸 참여」 §3).
    @Test
    fun `취소·종료된 룸에서도 방장과 참여자는 명부를 조회한다`() {
        listOf(RoomStatus.CANCELED, RoomStatus.COMPLETED).forEach { status ->
            updateRoomStatus(status)

            assertThatCode { roomParticipantService.getParticipants(hostMemberId, roomId) }
                .doesNotThrowAnyException()
            assertThatCode { roomParticipantService.getParticipants(participantMemberId, roomId) }
                .doesNotThrowAnyException()
        }
    }

    private fun assertForbidden(viewerMemberId: UUID) {
        assertThatThrownBy { roomParticipantService.getParticipants(viewerMemberId, roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)
            }
    }

    private fun persistRoom() {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "카카오 백엔드 2차 대비",
                description = null,
                interviewStage = InterviewStage.SECOND,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 3,
                maxCapacity = 5,
                startAt = startAt,
                durationMinutes = 90,
            ),
        )
    }

    private fun persistParticipation(
        memberId: UUID,
        role: ParticipationRole,
        status: ParticipationStatus = ParticipationStatus.JOINED,
        leftByMemberId: UUID? = null,
    ) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = role,
                status = status,
                joinedAt = joinedAt,
                leftByMemberId = leftByMemberId,
                leftAt = joinedAt.plusDays(1).takeIf { status == ParticipationStatus.LEFT },
            ),
        )
    }

    // 룸 상태를 바꾸는 전이 메서드가 아직 없다(MOI-396·398). 상태를 직접 심는다.
    private fun updateRoomStatus(status: RoomStatus) {
        entityManager.createNativeQuery("update room set status = :status where id = :roomId")
            .setParameter("status", status.name)
            .setParameter("roomId", roomId)
            .executeUpdate()
        entityManager.clear()
    }
}
