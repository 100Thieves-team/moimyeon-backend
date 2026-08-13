package io.plady.moimyeon.core.domain.room

// 방장 반려 사유(「룸 참여」 §4.4, D·03 모달과 1:1). 사유 없음은 값이 아니라 null 이다.
// 시스템 전이 사유(ROOM_CANCELED 등)는 RoomApplicationStatus 가 갖는 별개 축이라 여기 없다.
// 저장은 name 문자열(reject_reason VARCHAR(50)) — 컬럼에 코드 도입 전 자유 텍스트가 남아 있어
// 엔티티는 String 을 유지한다(MOI-451 D2-4).
enum class RejectReason {
    ROLE_MISMATCH,
    CAPACITY_FILLED,
    DIRECTION_MISMATCH,
}
