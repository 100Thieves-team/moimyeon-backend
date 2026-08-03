package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomDetail
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import java.time.LocalDateTime
import java.util.UUID

// 룸 단건 조회 응답 — 룸의 실제 저장 데이터 + 현재 인원 + 방장 식별자.
// 회사·공고·직무 표시명, 방장 프로필/신뢰 지표, 진행 방식 라벨 enrich 는 별도 이슈다(docs/room-progress.md).
data class RoomReadResponse(
    val roomId: UUID,
    val status: String, // RoomStatus (RECRUITING | CONFIRMED | COMPLETED | CANCELED)
    val jobPostingId: Long,
    val jobRoleId: Long,
    val title: String,
    val description: String?,
    val round: String, // InterviewStage
    val roundLabel: String,
    val type: String?, // InterviewType (선택)
    val typeLabel: String?,
    val method: String, // ONLINE | OFFLINE
    val sigunguId: Long?, // OFFLINE 일 때만
    val schedule: RoomReadScheduleResponse,
    val recruit: RoomReadRecruitResponse,
    val resumePublic: Boolean,
    val hostMemberId: UUID,
) {
    companion object {
        fun from(detail: RoomDetail): RoomReadResponse {
            val room = detail.room
            val (method, sigunguId) = when (val place = room.meetingPlace) {
                MeetingPlace.Online -> "ONLINE" to null
                is MeetingPlace.Offline -> "OFFLINE" to place.sigunguId
            }
            return RoomReadResponse(
                roomId = room.id,
                status = room.status.name,
                jobPostingId = room.jobPostingId,
                jobRoleId = room.jobRoleId,
                title = room.title.value,
                description = room.description?.value,
                round = room.interviewStage.name,
                roundLabel = room.interviewStage.label,
                type = room.interviewType?.name,
                typeLabel = room.interviewType?.label,
                method = method,
                sigunguId = sigunguId,
                schedule = RoomReadScheduleResponse(
                    startAt = room.schedule.startAt,
                    durationMinutes = room.schedule.durationMinutes,
                ),
                recruit = RoomReadRecruitResponse(
                    current = detail.currentParticipants,
                    min = room.capacity.min,
                    max = room.capacity.max,
                    // 모집중/마감은 저장값이 아니라 정원 충족 여부로 계산한다(핵심 결정).
                    recruitStatus = if (detail.currentParticipants >= room.capacity.max) "CLOSED" else "RECRUITING",
                ),
                resumePublic = room.resumeSharingPolicy == ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION,
                hostMemberId = detail.hostMemberId,
            )
        }
    }
}

data class RoomReadScheduleResponse(
    val startAt: LocalDateTime,
    val durationMinutes: Int,
)

data class RoomReadRecruitResponse(
    val current: Int,
    val min: Int,
    val max: Int,
    val recruitStatus: String, // RECRUITING | CLOSED (정원 충족 시 CLOSED)
)
