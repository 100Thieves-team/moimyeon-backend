package io.plady.moimyeon.core.domain.room

// 탐색 목록의 정렬(「룸 탐색」 §4.3). 두 키 모두 순회 도중 사실상 변하지 않아 커서 페이지네이션과 양립한다.
// 집계에서 나오는 정렬 키(모집 마감 임박순)는 참여가 생길 때마다 룸의 위치가 바뀌어 MOI-391 로 분리했다.
enum class RoomSortOrder {
    SCHEDULE, // start_at ASC, id ASC
    RECENT, // created_at DESC, id DESC
    ;

    companion object {
        val DEFAULT = SCHEDULE

        // 지원하지 않는 값과 누락은 기본 정렬로 떨어뜨린다. 필터의 "잘못된 값은 무시"와 같은 태도다(§4.7).
        fun from(raw: String?): RoomSortOrder = entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}
