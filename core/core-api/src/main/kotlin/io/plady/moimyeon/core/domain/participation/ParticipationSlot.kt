package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.enums.RoomStatus

// 참여 슬롯(「룸 참여」 §4.1) — 한 사람이 동시에 참여할 수 있는 룸 수.
//
// 규칙을 여기 하나가 갖는다. 신청 제출·방장 수락·자동 위임 셋이 같은 판정을 써야 하는데
// (MOI-427), 같은 개념 안에서 Implement 끼리 참조하지 않으므로(layers.md) Finder 와 Validator 가
// 서로를 부르는 대신 둘 다 이것을 본다.
object ParticipationSlot {
    // 대기 신청 한도(기본 3건)와 다른 축이다 — 대기 신청은 슬롯에 포함하지 않는다(§4.1, 20260804 확정).
    // PRD 는 둘 다 회원별 개인화를 예고하지만 아직 둘 다 상수다.
    const val MAX: Long = 3

    // 슬롯을 문 룸 상태. 완료·취소된 룸은 놓아준다(「룸 참여」 R45, 「진행 마무리」 R74).
    val OCCUPYING_ROOM_STATUSES: Set<RoomStatus> = setOf(
        RoomStatus.RECRUITING,
        RoomStatus.CONFIRMED,
    )

    fun isAvailable(occupiedCount: Long): Boolean = occupiedCount < MAX

    // 화면의 min 계산에 쓰이므로 점유가 한도를 넘어도 음수가 되지 않는다(ActiveRoomLimit.remaining 과 동형).
    fun remaining(occupiedCount: Long): Int = (MAX - occupiedCount).coerceAtLeast(0).toInt()
}
