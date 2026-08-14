package io.plady.moimyeon.core.domain.roomcomment

import io.plady.moimyeon.core.enums.RoomStatus
import java.time.Duration
import java.time.LocalDateTime

// 읽기 전용 판정(「룸 방명록」 §4.4). 전환 시각을 저장하지 않고 종료·취소 전이 시각 + 24시간을
// 조회·작성 시점마다 계산한다 - 매 요청이 같은 식을 평가하므로 배치가 없고, "전환 순간의
// 등록"(§4.6)은 별도 처리 없이 이 판정에서 거부된다.
// 유예 중(writable=true + readOnlyAt≠null)이 화면의 "곧 읽기 전용" 예고 배너 조합이다.
data class RoomCommentWindow(
    val writable: Boolean,
    val readOnlyAt: LocalDateTime?,
) {
    companion object {
        private val TERMINAL_STATUSES = setOf(RoomStatus.CANCELED, RoomStatus.COMPLETED)
        private val GRACE = Duration.ofHours(24)

        fun of(status: RoomStatus, terminalTransitionAt: LocalDateTime?, now: LocalDateTime): RoomCommentWindow {
            if (status !in TERMINAL_STATUSES) return RoomCommentWindow(writable = true, readOnlyAt = null)

            // 터미널 상태인데 전이 로그가 없으면 유예 시작점을 알 수 없다. 추정으로 열어 두는 대신 닫는다.
            if (terminalTransitionAt == null) return RoomCommentWindow(writable = false, readOnlyAt = null)

            val readOnlyAt = terminalTransitionAt.plus(GRACE)
            return RoomCommentWindow(writable = readOnlyAt.isAfter(now), readOnlyAt = readOnlyAt)
        }
    }
}
