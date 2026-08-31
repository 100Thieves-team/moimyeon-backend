package io.plady.moimyeon.core.api.controller.v1.response

import com.fasterxml.jackson.annotation.JsonFormat
import io.plady.moimyeon.core.domain.room.RoomSchedule
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// 룸 생성(POST /v1/rooms)의 응답. 생성 성공 시 상세로 이동하기 위한 최소 식별자.
// 룸 id 는 외부 노출 식별자라 UUID(UUIDv7 계열, ERD Step 4)다.
data class RoomCreatedResponse(
    val roomId: UUID,
    val status: String, // RECRUITING
)

data class RoomJobPostingResponse(
    val jobPostingId: Long,
    val postingName: String, // 프론트엔드 개발자 (결제플랫폼)
)

data class RoomRegionResponse(
    val sigunguId: Long,
    val label: String, // 서울 강남구
)

// 목록 카드의 일정. 서버는 값만 내려주고 표시 문구는 화면이 만든다.
// (요일·오전/오후·"90분" 같은 문구를 서버가 조립하면 i18n·디자인 변경이 서버 배포를 요구하게 된다.)
data class RoomScheduleResponse(
    val date: LocalDate,
    // Jackson 기본 LocalTime 직렬화는 "14:00:00"(ISO) → FE 계약(HH:mm)에 맞춰 "14:00"으로 고정.
    @get:JsonFormat(pattern = "HH:mm")
    val startTime: LocalTime,
    val durationMinutes: Int,
    // 서버 시각 기준 일정 경과(MOI-500). 신청·확정 검증과 같은 술어라 화면이 시계로 재계산하지 않는다.
    val isPassed: Boolean,
) {
    companion object {
        fun from(schedule: RoomSchedule, isPassed: Boolean): RoomScheduleResponse = RoomScheduleResponse(
            date = schedule.startAt.toLocalDate(),
            startTime = schedule.startAt.toLocalTime(),
            durationMinutes = schedule.durationMinutes,
            isPassed = isPassed,
        )
    }
}
