package io.plady.moimyeon.core.domain.roomviewer

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
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 이슈 「검증」 시나리오 그대로 — 같은 룸을 일곱 주체가 조회한다.
// 조회·판정·조립이 한 번에 걸리는 유일한 자리라 여기만 실물 빈으로 본다.
class RoomViewerReaderIT(
    private val roomViewerReader: RoomViewerReader,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val startAt: LocalDateTime = LocalDateTime.now().plusDays(7)
    private val createdAt: LocalDateTime = LocalDateTime.now().minusDays(5)
    private val seededMemberIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
        memberRepository.deleteAllById(seededMemberIds)
        seededMemberIds.clear()
    }

    @Test
    fun `같은 룸을 일곱 주체가 조회하면 각자의 관계가 나온다`() {
        val hostMemberId = seedRoom()
        val participant = seedParticipant()
        val applicant = seedApplicant(RoomApplicationStatus.PENDING)
        val withdrawer = seedApplicant(RoomApplicationStatus.WITHDRAWN)
        val rejected = seedApplicant(RoomApplicationStatus.REJECTED)
        val stranger = seedMember(UUID.randomUUID(), MemberStatus.ACTIVE)

        assertRelation(null, ViewerRelation.ANONYMOUS, ViewerAction.LOGIN_REQUIRED)
        assertRelation(hostMemberId, ViewerRelation.HOST, ViewerAction.MANAGE_ROOM)
        assertRelation(participant, ViewerRelation.PARTICIPANT, ViewerAction.VIEW_MY_ROOM)
        assertRelation(applicant, ViewerRelation.APPLIED, ViewerAction.VIEW_MY_APPLICATION)
        assertRelation(withdrawer, ViewerRelation.WITHDRAWN, ViewerAction.APPLY)
        assertRelation(stranger, ViewerRelation.NONE, ViewerAction.APPLY)

        val blocked = read(rejected)
        assertThat(blocked.relation).isEqualTo(ViewerRelation.REJECTED)
        assertThat(blocked.actions).isEmpty()
        assertThat(blocked.blockReason).isEqualTo(ViewerBlockReason.APPLICATION_REJECTED)
    }

    // 강퇴는 participation 의 left_by_member_id 로만 갈린다. 신청 이력은 수락된 채로 남는다.
    @Test
    fun `내보내진 사람과 스스로 나간 사람이 갈린다`() {
        val hostMemberId = seedRoom()
        val removed = seedLeftParticipant(leftBy = hostMemberId)
        val leaver = seedLeftParticipant(leftBy = null)

        val removedViewer = read(removed)
        assertThat(removedViewer.relation).isEqualTo(ViewerRelation.REMOVED)
        assertThat(removedViewer.blockReason).isEqualTo(ViewerBlockReason.REMOVED_FROM_ROOM)

        assertRelation(leaver, ViewerRelation.NONE, ViewerAction.APPLY)
    }

    private fun assertRelation(viewerMemberId: UUID?, relation: ViewerRelation, primaryAction: ViewerAction) {
        val viewer = read(viewerMemberId)

        assertThat(viewer.relation).describedAs("$relation").isEqualTo(relation)
        assertThat(viewer.actions.firstOrNull()).describedAs("$relation").isEqualTo(primaryAction)
        assertThat(viewer.blockReason).describedAs("$relation").isNull()
    }

    private fun read(viewerMemberId: UUID?): RoomViewer {
        val room = RoomApplicability(
            status = RoomStatus.RECRUITING,
            startAt = startAt,
            currentParticipants = 2,
            maxCapacity = 6,
        )
        return roomViewerReader.readAll(viewerMemberId, mapOf(roomId to room)).getValue(roomId)
    }

    private fun seedRoom(): UUID {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "뷰어 관계 테스트 룸",
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
        val hostMemberId = seedMember(UUID.randomUUID(), MemberStatus.ACTIVE)
        join(hostMemberId, ParticipationRole.HOST, ParticipationStatus.JOINED)
        return hostMemberId
    }

    private fun seedParticipant(): UUID {
        val memberId = seedMember(UUID.randomUUID(), MemberStatus.ACTIVE)
        join(memberId, ParticipationRole.PARTICIPANT, ParticipationStatus.JOINED)
        return memberId
    }

    private fun seedLeftParticipant(leftBy: UUID?): UUID {
        val memberId = seedMember(UUID.randomUUID(), MemberStatus.ACTIVE)
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.LEFT,
                joinedAt = createdAt,
                leftByMemberId = leftBy ?: memberId,
                leftAt = createdAt.plusDays(1),
            ),
        )
        return memberId
    }

    private fun seedApplicant(status: RoomApplicationStatus): UUID {
        val memberId = seedMember(UUID.randomUUID(), MemberStatus.ACTIVE)
        roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = memberId,
                note = "참여하고 싶습니다",
                appliedAt = createdAt,
                status = status,
                pendingMemberId = memberId.takeIf { status == RoomApplicationStatus.PENDING },
            ),
        )
        return memberId
    }

    private fun join(memberId: UUID, role: ParticipationRole, status: ParticipationStatus) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = role,
                status = status,
                joinedAt = createdAt,
            ),
        )
    }

    private fun seedMember(memberId: UUID, status: MemberStatus): UUID {
        val suffix = memberId.toString().take(8)
        memberRepository.saveAndFlush(
            MemberEntity(
                id = memberId,
                email = "viewer-$suffix@example.com",
                nickname = "뷰어$suffix",
                status = status,
                lastLoginAt = createdAt.minusDays(1),
                socialAccounts = listOf(
                    SocialAccountEntity(
                        provider = SocialLoginProvider.GOOGLE,
                        providerId = "viewer-$suffix",
                        linkedEmail = "viewer-$suffix@example.com",
                    ),
                ),
            ),
        )
        seededMemberIds += memberId
        return memberId
    }
}
