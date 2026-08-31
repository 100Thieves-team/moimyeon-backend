package io.plady.moimyeon.core.domain.roomviewer

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
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

// 같은 룸을 여러 주체가 조회했을 때 각자의 "사실"이 맞는지를 실물 빈으로 본다(MOI-500).
// 판정은 없다 — 버튼 판정은 화면이, 강제는 신청 경로 Validator 가 갖는다.
// 조회·조립이 한 번에 걸리는 유일한 자리라 여기만 실물 빈으로 본다.
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
    fun `같은 룸을 일곱 주체가 조회하면 각자의 사실이 나온다`() {
        val hostMemberId = seedRoom()
        val participant = seedParticipant()
        val applicant = seedApplicant(RoomApplicationStatus.PENDING)
        val withdrawer = seedApplicant(RoomApplicationStatus.WITHDRAWN)
        val rejected = seedApplicant(RoomApplicationStatus.REJECTED)
        val stranger = seedMember(UUID.randomUUID(), MemberStatus.ACTIVE)

        // 비로그인은 사실 자체가 없다 — 응답의 viewer 가 null 이 되는 원천이다.
        assertThat(roomViewerReader.readAll(null, setOf(roomId)).getValue(roomId)).isNull()

        val host = read(hostMemberId)
        assertThat(host.room.host).isTrue()
        assertThat(host.room.participating).isTrue() // 방장도 JOINED — 화면은 host 를 먼저 본다

        val joined = read(participant)
        assertThat(joined.room.host).isFalse()
        assertThat(joined.room.participating).isTrue()

        assertThat(read(applicant).room.latestApplication).isEqualTo(RoomApplicationStatus.PENDING)
        assertThat(read(withdrawer).room.latestApplication).isEqualTo(RoomApplicationStatus.WITHDRAWN)
        assertThat(read(rejected).room.latestApplication).isEqualTo(RoomApplicationStatus.REJECTED)

        val none = read(stranger)
        assertThat(none.room.host).isFalse()
        assertThat(none.room.participating).isFalse()
        assertThat(none.room.removed).isFalse()
        assertThat(none.room.latestApplication).isNull()
        assertThat(none.member.active).isTrue()
    }

    // 강퇴는 participation 의 left_by_member_id 로만 갈리고, 신청은 수락된 채 남는다.
    // 화면이 latestApplication 만 보면 강퇴가 안 보인다 — 두 사실이 "함께" 내려가는 것이 계약이다.
    @Test
    fun `강퇴자는 강퇴 이력과 수락된 신청이 함께 내려간다`() {
        val hostMemberId = seedRoom()
        val removed = seedLeftParticipant(leftBy = hostMemberId, withAcceptedApplication = true)

        val facts = read(removed)

        assertThat(facts.room.removed).isTrue()
        assertThat(facts.room.latestApplication).isEqualTo(RoomApplicationStatus.ACCEPTED)
        assertThat(facts.room.participating).isFalse()
    }

    // 자진 이탈은 재신청을 막지 않는다 — 강퇴 이력 없이 ACCEPTED 만 남는 조합이 그 신호다.
    @Test
    fun `스스로 나간 사람은 강퇴 이력 없이 수락 신청만 남는다`() {
        seedRoom()
        val leaver = seedLeftParticipant(leftBy = null, withAcceptedApplication = true)

        val facts = read(leaver)

        assertThat(facts.room.removed).isFalse()
        assertThat(facts.room.latestApplication).isEqualTo(RoomApplicationStatus.ACCEPTED)
        assertThat(facts.room.participating).isFalse()
    }

    // 회원 축 숫자는 신청 경로가 강제하는 집계와 같은 조회에서 나와야 한다(F5).
    // 모집 중인 이 룸의 참여는 슬롯을 물고, 대기 신청은 한도를 문다.
    @Test
    fun `슬롯과 대기 신청 숫자는 신청 경로와 같은 집계로 내려간다`() {
        seedRoom()
        val participant = seedParticipant()
        val applicant = seedApplicant(RoomApplicationStatus.PENDING)

        val joined = read(participant)
        assertThat(joined.member.participationSlots.occupied).isEqualTo(1L)
        assertThat(joined.member.participationSlots.limit).isEqualTo(3)
        assertThat(joined.member.pendingApplicationQuota.occupied).isEqualTo(0L)

        val applied = read(applicant)
        assertThat(applied.member.participationSlots.occupied).isEqualTo(0L)
        assertThat(applied.member.pendingApplicationQuota.occupied).isEqualTo(1L)
        assertThat(applied.member.pendingApplicationQuota.limit).isEqualTo(3)
    }

    private fun read(viewerMemberId: UUID): ViewerFacts {
        return checkNotNull(roomViewerReader.readAll(viewerMemberId, setOf(roomId)).getValue(roomId))
    }

    private fun seedRoom(): UUID {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "뷰어 사실 테스트 룸",
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

    private fun seedLeftParticipant(leftBy: UUID?, withAcceptedApplication: Boolean = false): UUID {
        val memberId = seedMember(UUID.randomUUID(), MemberStatus.ACTIVE)
        if (withAcceptedApplication) {
            roomApplicationRepository.saveAndFlush(
                RoomApplicationEntity(
                    roomId = roomId,
                    applicantMemberId = memberId,
                    note = "참여하고 싶습니다",
                    appliedAt = createdAt,
                    status = RoomApplicationStatus.ACCEPTED,
                    pendingMemberId = null,
                ),
            )
        }
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
