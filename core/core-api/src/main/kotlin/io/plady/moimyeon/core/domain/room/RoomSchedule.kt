package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import java.time.LocalDateTime

data class RoomSchedule(
    val startAt: LocalDateTime,
    val durationMinutes: Int,
) {
    init {
        val endAt: LocalDateTime = startAt.plusMinutes(durationMinutes.toLong())
        requireBusiness(durationMinutes > 0, CoreErrorType.INVALID_ROOM_SCHEDULE)
    }

    companion object {
        // 일정 경과 술어의 단일 소유자 — 시작 시각과 같아지는 순간부터 지난 것으로 본다.
        // 신청 검증(E1412)과 확정 검증(SCHEDULE_PASSED)이 같은 판정을 본다. 인라인 비교를 만들지 않는다.
        // 응답에는 경과 여부를 내리지 않는다(MOI-500) — startAt 이 원천 사실이고, 화면 판정이 어긋나면
        // 신청·확정 API 의 재검증이 잡는다. 엔티티 경로는 RoomSchedule 재구성 없이 이쪽을 부른다.
        fun isPassed(startAt: LocalDateTime, now: LocalDateTime): Boolean = !startAt.isAfter(now)
    }
}
