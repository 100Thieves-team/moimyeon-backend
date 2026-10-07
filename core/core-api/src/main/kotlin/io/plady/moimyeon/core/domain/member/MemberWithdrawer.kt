package io.plady.moimyeon.core.domain.member

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.notification.WebPushSubscriptionManager
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionManager
import io.plady.moimyeon.core.domain.session.SessionManager
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

// 탈퇴의 마지막 커밋: 회원 단위 정리와 삭제. 룸 나가기는 이보다 먼저 룸마다 따로 커밋된다(MemberService.withdraw).
@Component
class MemberWithdrawer(
    private val memberRepository: MemberRepository,
    private val participationFinder: ParticipationFinder,
    private val roomApplicationSubmissionManager: RoomApplicationSubmissionManager,
    private val sessionManager: SessionManager,
    private val webPushSubscriptionManager: WebPushSubscriptionManager,
) {
    // 회원을 먼저 잠근다(회원 → 신청, 참가 신청 제출과 같은 순서). 잠근 뒤에는 이 회원이 새 룸을 만들거나 신청할 수 없다.
    // 대기 신청을 새 참여 확인보다 먼저 닫는다 — 방장이 수락 중인 신청이면 그 신청 행 잠금을 기다리므로, 뒤이은 확인이
    // 수락 결과를 본다. 앞서 나간 룸 밖의 참여가 있으면 회원을 지우지 않고 false(룸 나가기부터 다시 돈다).
    @Transactional
    fun withdraw(memberId: UUID, now: LocalDateTime, leftRoomIds: Set<UUID>): Boolean {
        log.debug { "member.withdrawer.withdraw memberId=$memberId" }
        val member = requireFound(memberRepository.findForUpdateById(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        // 멱등이다(SSOT C.member.withdraw). 탈퇴 직후 남은 액세스 토큰으로 다시 눌러도 성공으로 끝난다.
        if (member.isDeleted()) return true
        roomApplicationSubmissionManager.withdrawAllPending(memberId, now)
        if (!leftRoomIds.containsAll(participationFinder.getParticipatingRoomIds(memberId))) return false
        sessionManager.closeAll(memberId, now)
        webPushSubscriptionManager.unregisterAll(memberId)
        member.delete(now)
        return true
    }
}
