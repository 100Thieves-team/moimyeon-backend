package io.plady.moimyeon.core.domain.roomviewer

import io.plady.moimyeon.core.enums.RoomApplicationStatus

// 로그인한 뷰어에 대해 조회된 사실 전부. 비로그인이면 이 객체 자체가 없다.
// 판정이 아니라 조회 결과다 — 규칙은 RoomViewerPolicy 만 갖는다.
data class ViewerFacts(
    val room: ViewerRoomFacts,
    val member: ViewerMemberFacts,
)

// 뷰어의 "이 룸에 대한" 사실. 룸마다 다르므로 목록에서는 룸 수만큼 필요하다.
data class ViewerRoomFacts(
    val host: Boolean,
    val participating: Boolean,
    // 방장이 내보낸 이력. 자진 이탈은 여기 포함되지 않는다 — 재신청을 막는 것은 강퇴뿐이다(D4).
    val removed: Boolean,
    // 이 룸에 대한 가장 최근 신청 1건의 상태. 철회 후 재신청이 가능해 이력이 여러 건일 수 있다.
    val latestApplication: RoomApplicationStatus?,
)

// 뷰어의 "회원 축" 사실. 룸과 무관해서 목록 한 페이지에 한 번만 읽으면 된다.
// 판정 자체는 MOI-379·427 이 이미 갖고 있고 여기는 그 결과를 담기만 한다.
data class ViewerMemberFacts(
    val active: Boolean,
    val participationSlotAvailable: Boolean,
    val applicationQuotaAvailable: Boolean,
)
