package io.plady.moimyeon.core.enums

// room_status_log 의 전이 주체. 배치(SYSTEM)가 일으킨 전이에 회원 id 를 채우면
// "그 회원이 상태를 바꿨다"는 거짓 기록이 되므로 주체를 따로 명시한다(MOI-471).
enum class RoomStatusLogHandlerType {
    MEMBER,
    SYSTEM,
}
