package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 방장 이탈은 이탈·위임(또는 취소)이 한 커밋이어야 한다 — 방장 없는 룸이 한순간도 존재하면 안 된다.
// 테스트에 @Transactional 을 두지 않는다: 바깥 트랜잭션이 있으면 커밋 시점이 가려진다(testing.md).
class RoomLeaveIT(
    private val roomLeaveManager: RoomLeaveManager,
    private val roomManager: RoomManager,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val startAt: LocalDateTime = LocalDateTime.now().plusDays(7)

    // 참여·신청 시각의 기준. 확정 후 이탈 판정이 "확정 시각 < 이탈 시각"이라 실제 현재보다 과거여야 한다.
    private val createdAt: LocalDateTime = LocalDateTime.now().minusDays(5)
    private val seededMemberIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId == roomId })
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
        memberRepository.deleteAllById(seededMemberIds)
    }

    @Test
    fun `방장이 나가면 가장 먼저 참여한 참여자가 방장이 된다`() {
        seedRoom()
        val earlier = seedParticipant(joinedAt = createdAt.plusHours(1))
        val later = seedParticipant(joinedAt = createdAt.plusHours(2))

        roomLeaveManager.leave(roomId, hostMemberId)

        assertThat(hostMemberIdOf(roomId)).isEqualTo(earlier)
        assertThat(roleOf(later)).isEqualTo(ParticipationRole.PARTICIPANT)
        // 전 방장의 role 은 HOST 로 남는다 — 최초 방장이 누구였는지가 사라지면 안 된다(MOI-397).
        assertThat(roleOf(hostMemberId)).isEqualTo(ParticipationRole.HOST)
        assertThat(statusOf(hostMemberId)).isEqualTo(ParticipationStatus.LEFT)
        assertThat(hostRows(roomId)).hasSize(1)
    }

    @Test
    fun `방장 혼자면 가장 먼저 신청한 대기 신청자를 수락해 방장으로 세운다`() {
        seedRoom()
        val earlier = seedPendingApplication(appliedAt = createdAt.plusHours(1))
        val later = seedPendingApplication(appliedAt = createdAt.plusHours(2))

        roomLeaveManager.leave(roomId, hostMemberId)

        assertThat(hostMemberIdOf(roomId)).isEqualTo(earlier)
        assertThat(statusOf(earlier)).isEqualTo(ParticipationStatus.JOINED)

        val accepted = applicationOf(earlier)
        assertThat(accepted.status).isEqualTo(RoomApplicationStatus.ACCEPTED)
        assertThat(accepted.handlerMemberId).isEqualTo(hostMemberId)
        assertThat(accepted.pendingMemberId).isNull()
        // 뒷순번은 건드리지 않는다 — 그 사람은 새 방장이 판단할 몫이다.
        assertThat(applicationOf(later).status).isEqualTo(RoomApplicationStatus.PENDING)
    }

    @Test
    fun `자격을 잃은 신청자는 건너뛴다`() {
        seedRoom()
        val restricted = seedPendingApplication(appliedAt = createdAt.plusHours(1), status = MemberStatus.RESTRICTED)
        val eligible = seedPendingApplication(appliedAt = createdAt.plusHours(2))

        roomLeaveManager.leave(roomId, hostMemberId)

        assertThat(hostMemberIdOf(roomId)).isEqualTo(eligible)
        assertThat(applicationOf(restricted).status).isEqualTo(RoomApplicationStatus.PENDING)
    }

    @Test
    fun `넘길 사람이 없으면 룸이 취소되고 대기 신청도 정리된다`() {
        seedRoom()
        val restricted = seedPendingApplication(appliedAt = createdAt.plusHours(2), status = MemberStatus.RESTRICTED)

        roomLeaveManager.leave(roomId, hostMemberId)

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.CANCELED)
        assertThat(applicationOf(restricted).status).isEqualTo(RoomApplicationStatus.ROOM_CANCELED)
        assertThat(statusOf(hostMemberId)).isEqualTo(ParticipationStatus.LEFT)
        assertThat(
            roomStatusLogRepository.findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, RoomStatus.CANCELED),
        ).isNotNull()
    }

    // 이탈이 거부되면 아무것도 남지 않는다. 위임 실패로 이탈이 되돌아가는 경로는
    // 룸을 CONFIRMED 로 만들 수 없어(최소 인원이 2 이상) 도달할 수 없다 — 거부 경로가 그 자리를 대신한다.
    @Test
    fun `이탈이 거부되면 참여도 룸도 그대로다`() {
        seedRoom()
        seedParticipant(joinedAt = createdAt.plusHours(1))
        roomManager.confirm(roomId, hostMemberId)

        assertThatThrownBy { roomLeaveManager.leave(roomId, hostMemberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_AT_MIN_CAPACITY)
            }

        assertThat(statusOf(hostMemberId)).isEqualTo(ParticipationStatus.JOINED)
        assertThat(hostMemberIdOf(roomId)).isEqualTo(hostMemberId)
        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.CONFIRMED)
    }

    // 확정 명단은 left_at 과 확정 시각의 대소로 갈린다. 나가기가 left_at 을 안 쓰면
    // 확정 후 이탈자가 명단에서 사라져 이력서 원본 권한·후기 대상이 조용히 틀어진다.
    // 확정과 이탈을 실제 흐름 그대로 잇는다 — 같은 초 안에 일어나므로 시각 정밀도가 곧 판정이다(MOI-428).
    @Test
    fun `확정 후 이탈자는 확정 참여자 판정에 걸린다`() {
        seedRoom()
        val participant = seedParticipant(joinedAt = createdAt.plusHours(1))
        seedParticipant(joinedAt = createdAt.plusHours(2))
        roomManager.confirm(roomId, hostMemberId)

        roomLeaveManager.leave(roomId, participant)

        assertThat(participationRepository.existsAtRoomConfirmation(roomId, participant)).isTrue()
    }

    @Test
    fun `확정 전 이탈자는 확정 참여자 판정에 걸리지 않는다`() {
        seedRoom()
        val leaver = seedParticipant(joinedAt = createdAt.plusHours(1))
        seedParticipant(joinedAt = createdAt.plusHours(1))

        roomLeaveManager.leave(roomId, leaver)
        roomManager.confirm(roomId, hostMemberId)

        assertThat(participationRepository.existsAtRoomConfirmation(roomId, leaver)).isFalse()
    }

    private fun rows() = participationRepository.findAll().filter { it.roomId == roomId }

    private fun roleOf(memberId: UUID) = rows().single { it.memberId == memberId }.participationRole

    private fun statusOf(memberId: UUID) = rows().single { it.memberId == memberId }.status

    private fun hostRows(roomId: UUID) = rows().filter {
        it.participationRole == ParticipationRole.HOST && it.status == ParticipationStatus.JOINED
    }

    private fun hostMemberIdOf(roomId: UUID) = hostRows(roomId).single().memberId

    private fun applicationOf(applicantMemberId: UUID) = roomApplicationRepository.findAll()
        .single { it.roomId == roomId && it.applicantMemberId == applicantMemberId }

    private fun seedRoom() {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "방장 자동 위임 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 6,
                startAt = startAt,
                durationMinutes = 60,
            ),
        )
        seedMember(hostMemberId, MemberStatus.ACTIVE)
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = hostMemberId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = createdAt,
            ),
        )
    }

    private fun seedParticipant(joinedAt: LocalDateTime): UUID {
        val memberId = seedMember(UUID.randomUUID(), MemberStatus.ACTIVE)
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = joinedAt,
            ),
        )
        return memberId
    }

    private fun seedPendingApplication(
        appliedAt: LocalDateTime,
        status: MemberStatus = MemberStatus.ACTIVE,
    ): UUID {
        val memberId = seedMember(UUID.randomUUID(), status)
        roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = memberId,
                note = "참여하고 싶습니다",
                appliedAt = appliedAt,
                status = RoomApplicationStatus.PENDING,
                pendingMemberId = memberId,
            ),
        )
        return memberId
    }

    private fun seedMember(memberId: UUID, status: MemberStatus): UUID {
        val suffix = memberId.toString().take(8)
        memberRepository.saveAndFlush(
            MemberEntity(
                id = memberId,
                email = "leave-$suffix@example.com",
                nickname = "나가기$suffix",
                status = status,
                lastLoginAt = createdAt.minusDays(1),
                socialAccounts = listOf(
                    SocialAccountEntity(
                        provider = SocialLoginProvider.GOOGLE,
                        providerId = "leave-$suffix",
                        linkedEmail = "leave-$suffix@example.com",
                    ),
                ),
            ),
        )
        seededMemberIds += memberId
        return memberId
    }
}
