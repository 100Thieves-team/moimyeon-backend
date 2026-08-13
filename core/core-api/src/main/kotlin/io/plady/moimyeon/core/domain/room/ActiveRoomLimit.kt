package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.RoomStatus

// 같은 (방장, 공고, 직무) 로 동시에 들 수 있는 활성 룸의 한도(PRD 「룸 생성」 §4.7).
// 막는 쪽(RoomManager)과 묻는 쪽(RoomFinder)이 같은 것을 봐야 화면의 경고와 서버 결과가 어긋나지 않는다.
//
// 활성을 상태 집합으로 정의한 이유: COMPLETED 로 가는 전이가 아직 없어(MOI-431 배치) 지금은
// 포함 여부가 관측되지 않지만, 배치가 켜지는 날 코드 변경 없이 한도가 풀려야 한다.
// RECRUITING 만 세면 확정하는 순간 자리가 풀려 같은 공고·직무로 여섯 개를 들 수 있다.
//
// ⚠️ MOI-427 의 참여 슬롯과 집합이 같지만 공유하지 않는다 — 축이 다르다
// (여기는 내가 방장인 룸, 저기는 내가 참여 중인 룸). 한쪽 정책이 바뀔 때 조용히 끌려간다.
object ActiveRoomLimit {
    const val MAX = 3

    val ACTIVE_STATUSES = setOf(RoomStatus.RECRUITING, RoomStatus.CONFIRMED, RoomStatus.IN_PROGRESS)

    fun isExceeded(activeRoomCount: Long): Boolean = activeRoomCount >= MAX

    // 화면이 일괄 생성 개수를 이 값으로 막는다(§4.4). 동시 요청으로 한도를 넘긴 상태(MOI-331)에서
    // 음수가 나가면 그 계산이 깨지므로 0 에서 멈춘다.
    fun remaining(activeRoomCount: Long): Int = (MAX - activeRoomCount).coerceAtLeast(0).toInt()
}
