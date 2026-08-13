package io.plady.moimyeon.core.domain.participation

import java.util.UUID

// 한 회원의 룸별 참여 상태. 방장·참여 중·강퇴 이력 세 축이 같은 participation 행 집합에서 나오므로
// 한 조회로 함께 판정한다 — 축마다 조회를 나누면 룸 목록에서 쿼리만 는다.
data class MemberRoomParticipation(
    val roomId: UUID,
    val host: Boolean,
    val joined: Boolean,
    // 방장이 내보낸 이력. 자진 이탈은 포함하지 않는다 — 재신청을 막는 것은 강퇴뿐이다.
    val removed: Boolean,
)
