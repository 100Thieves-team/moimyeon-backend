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

    // 일정 경과 판정 — 시작 시각과 같아지는 순간부터 지난 것으로 본다.
    fun isPassedAt(now: LocalDateTime): Boolean = isPassed(startAt, now)

    companion object {
        // 일정 경과 술어의 단일 소유자. 신청 검증(E1412)·확정 검증(SCHEDULE_PASSED)·목록·상세 응답(isPassed)이
        // 전부 이 판정을 본다 — 인라인 비교를 만들지 않는다. 한쪽만 경계가 바뀌면 화면과 서버가 갈린다.
        // 엔티티에서 바로 판정하는 경로는 RoomSchedule 을 재구성하지 않고 이쪽을 부른다.
        fun isPassed(startAt: LocalDateTime, now: LocalDateTime): Boolean = !startAt.isAfter(now)
    }
}
