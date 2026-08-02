package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType

// 룸 수정 입력 — 생성 이후 편집 가능한 필드만.
// 식별 참조(공고·직무)와 이력서(참조·원본 공개 여부)는 수정 대상이 아니다(공개 여부는 PRD §4.5상 별도 스코프).
data class RoomUpdateCommand(
    val title: RoomTitle,
    val description: RoomDescription?,
    val interviewStage: InterviewStage,
    val interviewType: InterviewType?,
    val meetingPlace: MeetingPlace,
    val capacity: RoomCapacity,
    val schedule: RoomSchedule,
)
