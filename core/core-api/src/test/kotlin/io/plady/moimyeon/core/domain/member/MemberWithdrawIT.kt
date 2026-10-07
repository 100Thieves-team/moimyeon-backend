package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.room.FIXED_NOW
import io.plady.moimyeon.core.domain.room.FixedClockTestConfiguration
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
import io.plady.moimyeon.storage.db.core.RefreshTokenEntity
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionEntity
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import java.util.UUID

@Import(FixedClockTestConfiguration::class)
class MemberWithdrawIT(
    private val memberService: MemberService,
    private val memberRestorer: MemberRestorer,
    private val socialAuthService: SocialAuthService,
    private val memberRepository: MemberRepository,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val webPushSubscriptionRepository: WebPushSubscriptionRepository,
) : ContextTest() {
    private val withdrawingMemberId = UUID.randomUUID()
    private val joinedAt: LocalDateTime = FIXED_NOW.minusDays(5)
    private val futureStartAt: LocalDateTime = FIXED_NOW.plusDays(7)
    private val seededMemberIds = mutableListOf<UUID>()
    private val seededRoomIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId in seededRoomIds })
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId in seededRoomIds })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId in seededRoomIds })
        refreshTokenRepository.deleteAll(refreshTokenRepository.findAll().filter { it.memberId in seededMemberIds })
        webPushSubscriptionRepository.deleteAll(
            webPushSubscriptionRepository.findAll().filter { it.memberId in seededMemberIds },
        )
        seededRoomIds.forEach { if (roomRepository.existsById(it)) roomRepository.deleteById(it) }
        memberRepository.deleteAllById(seededMemberIds)
    }

    @Test
    fun `확정 룸의 방장이 탈퇴하면 다음 참여자에게 방장이 넘어가고 룸은 모집 중으로 돌아간다`() {
        seedMember(withdrawingMemberId)
        val roomId = seedRoom(confirmed = true)
        seedParticipation(roomId, withdrawingMemberId, ParticipationRole.HOST, joinedAt)
        val successor = seedParticipation(roomId, seedMember(), ParticipationRole.PARTICIPANT, joinedAt.plusHours(1))
        seedParticipation(roomId, seedMember(), ParticipationRole.PARTICIPANT, joinedAt.plusHours(2))

        memberService.withdraw(withdrawingMemberId)

        assertThat(statusOf(roomId, withdrawingMemberId)).isEqualTo(ParticipationStatus.LEFT)
        assertThat(roleOf(roomId, successor)).isEqualTo(ParticipationRole.HOST)
        assertThat(roomStatusOf(roomId)).isEqualTo(RoomStatus.RECRUITING)
    }

    @Test
    fun `확정 룸의 참여자가 탈퇴해 최소 인원보다 적어지면 룸이 모집 중으로 돌아간다`() {
        seedMember(withdrawingMemberId)
        val roomId = seedRoom(confirmed = true, minCapacity = 2)
        seedParticipation(roomId, seedMember(), ParticipationRole.HOST, joinedAt)
        seedParticipation(roomId, withdrawingMemberId, ParticipationRole.PARTICIPANT, joinedAt.plusHours(1))

        memberService.withdraw(withdrawingMemberId)

        assertThat(statusOf(roomId, withdrawingMemberId)).isEqualTo(ParticipationStatus.LEFT)
        assertThat(roomStatusOf(roomId)).isEqualTo(RoomStatus.RECRUITING)
    }

    @Test
    fun `혼자 있는 방장이 탈퇴하면 룸이 취소된다`() {
        seedMember(withdrawingMemberId)
        val roomId = seedRoom(confirmed = false)
        seedParticipation(roomId, withdrawingMemberId, ParticipationRole.HOST, joinedAt)

        memberService.withdraw(withdrawingMemberId)

        assertThat(roomStatusOf(roomId)).isEqualTo(RoomStatus.CANCELED)
    }

    @Test
    fun `진행 예정 시각이 지난 확정 룸의 참여자로 탈퇴하면 참여자로 남는다`() {
        seedMember(withdrawingMemberId)
        val roomId = seedRoom(confirmed = true, startAt = FIXED_NOW.minusHours(1))
        seedParticipation(roomId, seedMember(), ParticipationRole.HOST, joinedAt)
        seedParticipation(roomId, withdrawingMemberId, ParticipationRole.PARTICIPANT, joinedAt.plusHours(1))

        memberService.withdraw(withdrawingMemberId)

        assertThat(statusOf(roomId, withdrawingMemberId)).isEqualTo(ParticipationStatus.JOINED)
        assertThat(roomStatusOf(roomId)).isEqualTo(RoomStatus.CONFIRMED)
    }

    @Test
    fun `탈퇴하면 대기 신청 철회와 세션 폐기와 웹 푸시 등록 삭제와 회원 삭제가 함께 반영된다`() {
        seedMember(withdrawingMemberId)
        val otherRoomId = seedRoom(confirmed = false)
        seedParticipation(otherRoomId, seedMember(), ParticipationRole.HOST, joinedAt)
        val applicationId = seedPendingApplication(otherRoomId, withdrawingMemberId)
        refreshTokenRepository.saveAndFlush(
            RefreshTokenEntity(
                tokenHash = "withdraw-${UUID.randomUUID()}",
                memberId = withdrawingMemberId,
                expiresAt = FIXED_NOW.plusDays(1),
            ),
        )
        webPushSubscriptionRepository.saveAndFlush(
            WebPushSubscriptionEntity(
                memberId = withdrawingMemberId,
                registration = "registration-$withdrawingMemberId",
                registrationHash = withdrawingMemberId.toString().replace("-", "").padEnd(64, '0'),
                registeredAt = joinedAt,
            ),
        )

        memberService.withdraw(withdrawingMemberId)

        assertThat(roomApplicationRepository.findById(applicationId).orElseThrow().status)
            .isEqualTo(RoomApplicationStatus.WITHDRAWN)
        assertThat(refreshTokenRepository.findAll().filter { it.memberId == withdrawingMemberId })
            .allMatch { it.revokedAt != null }
        assertThat(webPushSubscriptionRepository.findAllByMemberId(withdrawingMemberId)).isEmpty()
        assertThat(memberRepository.findById(withdrawingMemberId).orElseThrow().isDeleted()).isTrue()
    }

    @Test
    fun `완료된 룸의 참여 기록은 탈퇴 후에도 그대로다`() {
        seedMember(withdrawingMemberId)
        val roomId = seedRoom(confirmed = true, completed = true)
        seedParticipation(roomId, seedMember(), ParticipationRole.HOST, joinedAt)
        seedParticipation(roomId, withdrawingMemberId, ParticipationRole.PARTICIPANT, joinedAt.plusHours(1))

        memberService.withdraw(withdrawingMemberId)

        assertThat(statusOf(roomId, withdrawingMemberId)).isEqualTo(ParticipationStatus.JOINED)
        assertThat(roomStatusOf(roomId)).isEqualTo(RoomStatus.COMPLETED)
    }

    @Test
    fun `탈퇴한 신원으로 다시 로그인하면 새로 가입하지 않고 복구 확인 대상이 된다`() {
        seedMember(withdrawingMemberId)
        memberService.withdraw(withdrawingMemberId)

        val result = socialAuthService.authenticate(
            SocialLoginProvider.GOOGLE,
            providerIdOf(withdrawingMemberId),
            Email("withdraw@example.com"),
        )

        assertThat(result).isEqualTo(SocialAuthentication.Withdrawn(withdrawingMemberId))
        assertThat(memberRepository.findById(withdrawingMemberId).orElseThrow().isDeleted()).isTrue()
    }

    @Test
    fun `복구하면 다시 로그인되고 나간 룸은 돌아오지 않는다`() {
        seedMember(withdrawingMemberId)
        val roomId = seedRoom(confirmed = false)
        seedParticipation(roomId, seedMember(), ParticipationRole.HOST, joinedAt)
        seedParticipation(roomId, withdrawingMemberId, ParticipationRole.PARTICIPANT, joinedAt.plusHours(1))
        memberService.withdraw(withdrawingMemberId)

        memberRestorer.restore(withdrawingMemberId, LocalDateTime.now(), LocalDateTime.now())

        assertThat(memberRepository.findById(withdrawingMemberId).orElseThrow().isDeleted()).isFalse()
        assertThat(statusOf(roomId, withdrawingMemberId)).isEqualTo(ParticipationStatus.LEFT)
        val relogin = socialAuthService.authenticate(
            SocialLoginProvider.GOOGLE,
            providerIdOf(withdrawingMemberId),
            Email("withdraw@example.com"),
        )
        assertThat(relogin).isEqualTo(SocialAuthentication.LoggedIn(withdrawingMemberId))
    }

    private fun providerIdOf(memberId: UUID) = "withdraw-${memberId.toString().take(8)}"

    private fun rows(roomId: UUID) = participationRepository.findAll().filter { it.roomId == roomId }

    private fun statusOf(roomId: UUID, memberId: UUID) = rows(roomId).single { it.memberId == memberId }.status

    private fun roleOf(roomId: UUID, memberId: UUID) = rows(roomId).single {
        it.memberId == memberId && it.status == ParticipationStatus.JOINED
    }.participationRole

    private fun roomStatusOf(roomId: UUID) = roomRepository.findById(roomId).orElseThrow().status

    private fun seedRoom(
        confirmed: Boolean,
        completed: Boolean = false,
        minCapacity: Short = 2,
        startAt: LocalDateTime = futureStartAt,
    ): UUID {
        val roomId = UUID.randomUUID()
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "탈퇴 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = minCapacity,
            maxCapacity = 6,
            startAt = startAt,
            durationMinutes = 60,
        )
        if (confirmed) room.confirm()
        if (completed) room.complete()
        roomRepository.saveAndFlush(room)
        seededRoomIds += roomId
        if (confirmed) {
            roomStatusLogRepository.saveAndFlush(
                RoomStatusLogEntity.byMember(
                    roomId = roomId,
                    transitionType = RoomStatus.CONFIRMED,
                    handlerMemberId = withdrawingMemberId,
                    occurredAt = joinedAt.plusDays(1),
                ),
            )
        }
        return roomId
    }

    private fun seedParticipation(
        roomId: UUID,
        memberId: UUID,
        role: ParticipationRole,
        at: LocalDateTime,
    ): UUID {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = role,
                status = ParticipationStatus.JOINED,
                joinedAt = at,
            ),
        )
        return memberId
    }

    private fun seedPendingApplication(roomId: UUID, memberId: UUID): Long = roomApplicationRepository.saveAndFlush(
        RoomApplicationEntity(
            roomId = roomId,
            applicantMemberId = memberId,
            note = "참여하고 싶습니다",
            appliedAt = joinedAt.plusHours(3),
            status = RoomApplicationStatus.PENDING,
            pendingMemberId = memberId,
        ),
    ).id

    private fun seedMember(memberId: UUID = UUID.randomUUID()): UUID {
        val suffix = memberId.toString().take(8)
        memberRepository.saveAndFlush(
            MemberEntity(
                id = memberId,
                email = "withdraw-$suffix@example.com",
                nickname = "탈퇴$suffix",
                status = MemberStatus.ACTIVE,
                lastLoginAt = joinedAt,
                socialAccounts = listOf(
                    SocialAccountEntity(
                        provider = SocialLoginProvider.GOOGLE,
                        providerId = "withdraw-$suffix",
                        linkedEmail = "withdraw-$suffix@example.com",
                    ),
                ),
            ),
        )
        seededMemberIds += memberId
        return memberId
    }
}
