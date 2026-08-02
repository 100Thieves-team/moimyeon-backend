package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy

// 룸 생성 요청(CreateRoomRequest)을 도메인 입력으로 옮긴 값.
// API 레이어에서 enum 파싱과 값 객체 조립까지 끝낸 상태로 넘어온다.
data class RoomCreationCommand(
    val jobPostingId: Long,
    val jobRoleId: Long,
    val title: RoomTitle,
    val description: RoomDescription?,
    val interviewStage: InterviewStage,
    val interviewType: InterviewType?,
    val meetingPlace: MeetingPlace,
    val capacity: RoomCapacity,
    val schedule: RoomSchedule,
    val resumeSharingPolicy: ResumeSharingPolicy,
    // 방장 이력서 참조. 이력서 보관함(J3, 준서 트랙) 완성 전까지는 존재·소유 검증 없이 참조값으로만 받는다.
    val resumeId: Long,
)
