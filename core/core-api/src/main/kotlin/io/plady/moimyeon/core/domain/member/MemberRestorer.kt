package io.plady.moimyeon.core.domain.member

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

// 탈퇴 계정 복구(「회원 및 프로필」 R175~R178). 탈퇴 시각만 지운다 — 이용 제한 상태는 별개 값이라 그대로 남고,
// 나간 룸·철회된 신청·끝난 세션은 되돌리지 않는다.
@Component
class MemberRestorer(
    private val memberRepository: MemberRepository,
) {
    // confirmedAt 은 복구 확인 토큰을 발급한 시각이다. 그보다 뒤에 다시 탈퇴했다면 그 토큰은 지난 탈퇴의 것이라
    // 쓸 수 없다 — "복구 → 다시 탈퇴" 뒤 남은 토큰으로 되살리지 못하게 한다. 토큰 시각이 초 단위라 1초 여유를 둔다.
    @Transactional
    fun restore(memberId: UUID, confirmedAt: LocalDateTime, now: LocalDateTime) {
        log.debug { "member.restorer.restore memberId=$memberId" }
        val member = requireFound(memberRepository.findForUpdateById(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        requireBusiness(!member.isDeletedAfter(confirmedAt.plusSeconds(1)), CoreErrorType.RESTORATION_EXPIRED)
        // 멱등이다(SSOT C.member.restore). 복구를 두 번 눌러도 두 번째는 로그인만 다시 된다.
        if (member.isDeleted()) member.active()
        member.loggedIn(now)
    }
}
