package io.plady.moimyeon.core.domain.roomcomment

import java.time.Duration
import java.time.LocalDateTime

// 중복 등록 판정(「룸 방명록」 §4.6). 더블클릭·재시도를 FE 협조 없이 서버 단독으로 덮는다 -
// 중복이면 에러가 아니라 기존 글을 돌려준다(룸 생성 MOI-331 과 같은 태도).
object RoomCommentDuplicate {
    private val WINDOW = Duration.ofSeconds(10)

    fun isDuplicate(
        lastContent: String,
        lastCreatedAt: LocalDateTime,
        content: String,
        now: LocalDateTime,
    ): Boolean = lastContent == content && Duration.between(lastCreatedAt, now) <= WINDOW
}
