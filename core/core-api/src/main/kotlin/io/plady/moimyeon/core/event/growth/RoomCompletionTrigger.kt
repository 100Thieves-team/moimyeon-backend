package io.plady.moimyeon.core.event.growth

// room.completed 의 completedBy 속성. 방장이 완료했는지, 일정 경과로 자동 완료됐는지 구분한다.
enum class RoomCompletionTrigger {
    HOST,
    SYSTEM,
}
