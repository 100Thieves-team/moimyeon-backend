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
        // 판정에 실제로 쓰는 것은 넷뿐이다 — 상태·시작 시각·최소 인원·현재 인원.
        // 확정 실행은 룸 행을 잠근 뒤 이 넷만 손에 쥐므로(RoomDetail 을 만들 수 없다) 이쪽을 부른다.
        // RoomDetail 을 받는 아래 시그니처가 여기로 위임해, 조회와 실행이 같은 판정을 탄다.
        //
        // 도메인 Room 을 받지 않는 이유 — 엔티티를 Room 으로 되살리려면 제목·설명까지 값 객체로
        // 재구성해야 하고, 그러면 확정이 제목 검증에 걸린다. 나중에 제목 규칙이 조여지면
        // 이미 저장된 룸이 확정 불가가 된다. 판정에 쓰지도 않는 값 때문에 그런 결합을 만들지 않는다.
        fun of(
            status: RoomStatus,
            startAt: LocalDateTime,
            minCapacity: Int,
            currentParticipants: Int,
            now: LocalDateTime,
        ): RoomConfirmation {
            val blockReason = blockReasonOf(status, startAt, minCapacity, currentParticipants, now)
            return RoomConfirmation(ready = blockReason == null, blockReason = blockReason)
        }

        fun of(detail: RoomDetail, now: LocalDateTime): RoomConfirmation = of(
            status = detail.room.status,
            startAt = detail.room.schedule.startAt,
            minCapacity = detail.room.capacity.min,
            currentParticipants = detail.currentParticipants,
            now = now,
        )

        // 먼저 걸린 사유 하나만 남긴다. 화면은 사유 하나로 문구를 고른다.
        // when(status) 로 받아 룸 상태가 늘어나면 컴파일이 먼저 막는다.
        private fun blockReasonOf(
            status: RoomStatus,
            startAt: LocalDateTime,
            minCapacity: Int,
            currentParticipants: Int,
            now: LocalDateTime,
        ): RoomConfirmationBlockReason? {
            return when (status) {
                RoomStatus.CONFIRMED -> RoomConfirmationBlockReason.ROOM_CONFIRMED
                RoomStatus.IN_PROGRESS -> RoomConfirmationBlockReason.ROOM_IN_PROGRESS
                RoomStatus.COMPLETED -> RoomConfirmationBlockReason.ROOM_COMPLETED
                RoomStatus.CANCELED -> RoomConfirmationBlockReason.ROOM_CANCELED
                RoomStatus.RECRUITING -> when {
                    // 시작 시각과 같아지는 순간부터 지난 것으로 본다.
                    !startAt.isAfter(now) -> RoomConfirmationBlockReason.SCHEDULE_PASSED
                    currentParticipants < minCapacity -> RoomConfirmationBlockReason.BELOW_MIN_CAPACITY
                    else -> null
                }
            }
        }
    }
}

// 선언 순서가 곧 판정 순서다. 이미 확정된 룸에 인원 미달을 묻지 않는다.
enum class RoomConfirmationBlockReason {
    ROOM_CONFIRMED,
    ROOM_IN_PROGRESS,
    ROOM_COMPLETED,
    ROOM_CANCELED,
    SCHEDULE_PASSED,
    BELOW_MIN_CAPACITY,
}
