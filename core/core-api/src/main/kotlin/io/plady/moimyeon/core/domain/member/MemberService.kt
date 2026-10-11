package io.plady.moimyeon.core.domain.member

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.analytics.AnalyticsIdGenerator
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.RoomLeaveManager
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionManager
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class MemberService(
    private val memberFinder: MemberFinder,
    private val nicknameGenerator: NicknameGenerator,
    private val memberWithdrawer: MemberWithdrawer,
    private val memberRestorer: MemberRestorer,
    private val roomApplicationSubmissionManager: RoomApplicationSubmissionManager,
    private val participationFinder: ParticipationFinder,
    private val roomLeaveManager: RoomLeaveManager,
    private val analyticsIdGenerator: AnalyticsIdGenerator,
    private val clock: Clock,
) {
    fun getMember(memberId: UUID): Member = memberFinder.getById(memberId)

    fun getMembers(memberIds: Collection<UUID>): List<Member> = memberFinder.getAllByIds(memberIds)

    fun getAnalyticsId(memberId: UUID): String? = analyticsIdGenerator.generate(memberId)

    fun suggestNickname(): Nickname = nicknameGenerator.generateUnique()

    fun isNicknameAvailable(rawNickname: String): Boolean {
        return memberFinder.isNicknameAvailable(Nickname(rawNickname))
    }

    // 탈퇴(「회원 및 프로필」 R169). 신청 철회 → 룸마다 나가기 → 회원 정리를 각각 커밋한다.
    // 한 트랜잭션으로 묶지 않는다: 여러 룸을 차례로 잠그면 뒤 룸의 조회가 앞서 만든 스냅샷을 봐 다른 요청의 변경을 덮어쓴다.
    // 중간에 실패해도 각 단계는 정상 상태이고, 다시 요청하면 남은 룸부터 이어서 끝난다.
    // 룸을 나가는 사이 새 참여가 생기면(다른 탭에서 룸 생성, 신청 수락) 회원 정리가 거절하고 처음부터 다시 돈다.
    fun withdraw(memberId: UUID) {
        log.debug { "member.withdraw memberId=$memberId" }
        val now = LocalDateTime.now(clock)
        repeat(WITHDRAW_ATTEMPTS) {
            roomApplicationSubmissionManager.withdrawAllPending(memberId, now)
            val roomIds = participationFinder.getParticipatingRoomIds(memberId).sorted()
            roomIds.forEach { roomLeaveManager.leaveOnWithdrawal(it, memberId, now) }
            if (memberWithdrawer.withdraw(memberId, now, roomIds.toSet())) return
        }
        throw CoreException(CoreErrorType.MEMBER_WITHDRAWAL_INTERRUPTED)
    }

    fun restore(memberId: UUID, confirmedAt: Instant) {
        log.debug { "member.restore memberId=$memberId" }
        memberRestorer.restore(memberId, LocalDateTime.ofInstant(confirmedAt, clock.zone), LocalDateTime.now(clock))
    }
}

private const val WITHDRAW_ATTEMPTS = 3
