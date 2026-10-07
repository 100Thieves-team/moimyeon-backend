package io.plady.moimyeon.core.domain.room

import java.time.LocalDateTime
import java.util.UUID

// 목록 순회의 "여기까지 읽었다" 지점.
//
// 정렬 키 값만으로는 동점 구간을 가를 수 없다 — 같은 start_at 이 페이지 경계에 걸리면
// '>' 는 동점을 통째로 건너뛰고 '>=' 는 통째로 다시 준다. 그래서 유일한 보조 키(id)를 함께 든다.
//
// 문자열 토큰으로의 인코딩은 이 객체가 갖지 않는다. 그건 와이어 포맷이라 web 레이어가 소유한다
// (core.api.controller.v1.request.RoomSearchCursorToken).
data class RoomCursor(
    val sortValue: LocalDateTime, // SCHEDULE 이면 start_at, RECENT 면 created_at
    val id: UUID,
)
