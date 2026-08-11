package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.RoomStatus
import java.time.LocalDateTime

// "이 룸이 확정될 준비가 됐나"(「진행 확정」 §4.1). 뷰어와 무관한 룸의 사실이며,
// "당신이 확정할 수 있나"라는 권한 축과 섞지 않는다.
//
// 조회(룸 상세)와 확정 실행이 같은 판정을 써야 화면의 버튼 상태와 실제 결과가 어긋나지 않는다.
// DB 를 보지 않고 이미 조회한 RoomDetail 에서 파생하므로 확정 실행도 락 안에서 그대로 호출할 수 있다.
data class RoomConfirmation(
    val ready: Boolean,
    val blockReason: RoomConfirmationBlockReason?,
) {
    companion object {
        fun of(detail: RoomDetail, now: LocalDateTime): RoomConfirmation {
            val blockReason = blockReasonOf(detail, now)
            return RoomConfirmation(ready = blockReason == null, blockReason = blockReason)
        }

        // 먼저 걸린 사유 하나만 남긴다. 화면은 사유 하나로 문구를 고른다.
        private fun blockReasonOf(detail: RoomDetail, now: LocalDateTime): RoomConfirmationBlockReason? {
            val room = detail.room
            return when {
                room.status == RoomStatus.CONFIRMED -> RoomConfirmationBlockReason.ROOM_CONFIRMED
                room.status == RoomStatus.COMPLETED -> RoomConfirmationBlockReason.ROOM_COMPLETED
                room.status == RoomStatus.CANCELED -> RoomConfirmationBlockReason.ROOM_CANCELED
                // 시작 시각과 같아지는 순간부터 지난 것으로 본다.
                !room.schedule.startAt.isAfter(now) -> RoomConfirmationBlockReason.SCHEDULE_PASSED
                detail.currentParticipants < room.capacity.min -> RoomConfirmationBlockReason.BELOW_MIN_CAPACITY
                else -> null
            }
        }
    }
}

// 선언 순서가 곧 판정 순서다. 이미 확정된 룸에 인원 미달을 묻지 않는다.
enum class RoomConfirmationBlockReason {
    ROOM_CONFIRMED,
    ROOM_COMPLETED,
    ROOM_CANCELED,
    SCHEDULE_PASSED,
    BELOW_MIN_CAPACITY,
}
