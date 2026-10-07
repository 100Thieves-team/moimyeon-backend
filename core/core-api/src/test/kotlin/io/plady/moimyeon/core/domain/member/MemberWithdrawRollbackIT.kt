package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.notification.WebPushSubscriptionManager
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
import io.plady.moimyeon.storage.db.core.RefreshTokenEntity
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

// 탈퇴의 마지막 커밋(회원 정리)에서 웹 푸시 등록 삭제를 실패시킨다. 그 커밋 안의 세션 폐기·회원 삭제는 함께 롤백되고,
// 앞서 따로 커밋한 룸 나가기·신청 철회는 남는다(MemberService.withdraw).
@Import(MemberWithdrawRollbackIT.FailingWebPushConfig::class)
class MemberWithdrawRollbackIT(
    private val memberService: MemberService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberRepository: MemberRepository,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
) : ContextTest() {
    private val memberId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()
    private val otherRoomId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 10, 2, 12, 0)

    @AfterEach
    fun cleanUp() {
        val roomIds = listOf(roomId, otherRoomId)
        roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId in roomIds })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId in roomIds })
        roomIds.forEach { if (roomRepository.existsById(it)) roomRepository.deleteById(it) }
        refreshTokenRepository.deleteAll(refreshTokenRepository.findAll().filter { it.memberId == memberId })
        memberRepository.deleteAllById(listOf(memberId, hostId))
    }

    @Test
    fun `회원 정리 커밋이 실패하면 세션 폐기와 회원 삭제는 남지 않고 이미 나간 룸과 철회된 신청은 남는다`() {
        persistMember(memberId)
        persistMember(hostId)
        persistRoom(roomId)
        persistRoom(otherRoomId)
        persistParticipation(roomId, hostId, ParticipationRole.HOST)
        persistParticipation(roomId, memberId, ParticipationRole.PARTICIPANT)
        persistParticipation(otherRoomId, hostId, ParticipationRole.HOST)
        val applicationId = persistPendingApplication(otherRoomId, memberId)
        refreshTokenRepository.saveAndFlush(
            RefreshTokenEntity(tokenHash = "rollback-$memberId", memberId = memberId, expiresAt = now.plusDays(365)),
        )

        assertThatThrownBy { memberService.withdraw(memberId) }.isInstanceOf(IllegalStateException::class.java)

        assertThat(memberRepository.findById(memberId).orElseThrow().isDeleted()).isFalse()
        assertThat(refreshTokenRepository.findAll().single { it.memberId == memberId }.revokedAt).isNull()
        assertThat(participationRepository.findAll().single { it.roomId == roomId && it.memberId == memberId }.status)
            .isEqualTo(ParticipationStatus.LEFT)
        assertThat(roomApplicationRepository.findById(applicationId).orElseThrow().status)
            .isEqualTo(RoomApplicationStatus.WITHDRAWN)
    }

    private fun persistMember(id: UUID) {
        val suffix = id.toString().take(8)
        memberRepository.saveAndFlush(
            MemberEntity(
                id = id,
                email = "rollback-$suffix@example.com",
                nickname = "롤백$suffix",
                status = MemberStatus.ACTIVE,
                lastLoginAt = now.minusDays(1),
                socialAccounts = listOf(SocialAccountEntity(SocialLoginProvider.GOOGLE, "rollback-$suffix", "rollback-$suffix@example.com")),
            ),
        )
    }

    private fun persistRoom(id: UUID) {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = id,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "탈퇴 롤백 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 6,
                startAt = now.plusDays(7),
                durationMinutes = 60,
            ),
        )
    }

    private fun persistParticipation(roomId: UUID, memberId: UUID, role: ParticipationRole) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = role,
                status = ParticipationStatus.JOINED,
                joinedAt = now.minusDays(2),
            ),
        )
    }

    private fun persistPendingApplication(roomId: UUID, memberId: UUID): Long = roomApplicationRepository.saveAndFlush(
        RoomApplicationEntity(
            roomId = roomId,
            applicantMemberId = memberId,
            note = "참여하고 싶습니다",
            appliedAt = now.minusDays(1),
            status = RoomApplicationStatus.PENDING,
            pendingMemberId = memberId,
        ),
    ).id

    @TestConfiguration(proxyBeanMethods = false)
    class FailingWebPushConfig {
        @Bean
        @Primary
        fun failingWebPushSubscriptionManager(
            webPushSubscriptionRepository: WebPushSubscriptionRepository,
            clock: Clock,
        ): WebPushSubscriptionManager = FailingWebPushSubscriptionManager(webPushSubscriptionRepository, clock)
    }

    // 트랜잭션 프록시가 서브클래스를 만들 수 있게 이름 있는 open 클래스로 둔다(익명 객체는 final 이다).
    open class FailingWebPushSubscriptionManager(
        webPushSubscriptionRepository: WebPushSubscriptionRepository,
        clock: Clock,
    ) : WebPushSubscriptionManager(webPushSubscriptionRepository, clock) {
        override fun unregisterAll(memberId: UUID): Unit = throw IllegalStateException("주입한 실패")
    }
}
