package io.plady.moimyeon.core.domain.member

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionManager
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class MemberWithdrawer(
    private val memberRepository: MemberRepository,
    private val roomApplicationSubmissionManager: RoomApplicationSubmissionManager,
) {
    // 참가 신청 제출과 같은 순서(회원 → 신청)로 잠가 교착을 막는다.
    @Transactional
    fun withdraw(memberId: UUID, now: LocalDateTime) {
        log.debug { "member.withdrawer.withdraw memberId=$memberId" }
        val member = requireFound(memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        roomApplicationSubmissionManager.withdrawAllPending(memberId, now)
        member.delete(now)
    }
}
