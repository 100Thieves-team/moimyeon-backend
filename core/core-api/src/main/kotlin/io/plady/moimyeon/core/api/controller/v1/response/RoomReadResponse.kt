package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomConfirmation
import io.plady.moimyeon.core.domain.room.RoomConfirmationBlockReason
import io.plady.moimyeon.core.domain.room.RoomDetail
import io.plady.moimyeon.core.domain.roomviewer.RoomViewer
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import java.time.LocalDateTime
import java.util.UUID

// 룸 단건 조회 응답 — 룸의 실제 저장 데이터 + 현재 인원 + 방장 식별자.
// 회사·공고·직무 표시명, 방장 프로필/신뢰 지표, 진행 방식 라벨 enrich 는 별도 이슈다(docs/room-progress.md).
data class RoomReadResponse(
    val roomId: UUID,
    val status: String, // RoomStatus (RECRUITING | CONFIRMED | IN_PROGRESS | COMPLETED | CANCELED)
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
    val confirmation: RoomReadConfirmationResponse,
    // "당신이 지금 무엇을 할 수 있나"(MOI-387). 위 confirmation 은 뷰어와 무관한 룸의 사실이라 축이 다르다.
    val viewer: RoomViewerResponse,
) {
    companion object {
        fun from(detail: RoomDetail, confirmation: RoomConfirmation, viewer: RoomViewer): RoomReadResponse {
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
                    pendingApplicationCount = detail.pendingApplicationCount,
                ),
                resumePublic = room.resumeSharingPolicy == ResumeSharingPolicy.ORIGINAL_AFTER_CONFIRMATION,
                hostMemberId = detail.hostMemberId,
                confirmation = RoomReadConfirmationResponse.from(confirmation, detail),
                viewer = RoomViewerResponse.from(viewer),
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
    // 「룸 참여」 §4.1·§6 이 공개로 지정한 값이다. 수만 공개하고 대기자 목록은 방장 외 비공개다.
    val pendingApplicationCount: Int,
)

// "이 룸이 확정될 준비가 됐나"(「진행 확정」 §4.1). 뷰어와 무관한 룸의 사실이며,
// "당신이 확정할 수 있나"는 뷰어 관계가 따로 답한다.
data class RoomReadConfirmationResponse(
    val ready: Boolean,
    val blockReason: RoomReadBlockReasonResponse?,
) {
    companion object {
        fun from(confirmation: RoomConfirmation, detail: RoomDetail): RoomReadConfirmationResponse {
            return RoomReadConfirmationResponse(
                ready = confirmation.ready,
                blockReason = confirmation.blockReason?.let { RoomReadBlockReasonResponse.from(it, detail) },
            )
        }
    }
}

// 라벨은 서버가 소유한다. 인원 미달 문구에는 현재 인원과 최소 인원이 수치로 들어간다.
data class RoomReadBlockReasonResponse(
    val code: String,
    val label: String,
) {
    companion object {
        fun from(reason: RoomConfirmationBlockReason, detail: RoomDetail): RoomReadBlockReasonResponse {
            val label = when (reason) {
                RoomConfirmationBlockReason.ROOM_CONFIRMED -> "이미 확정된 룸이에요"
                RoomConfirmationBlockReason.ROOM_IN_PROGRESS -> "진행 중인 룸이에요"
                RoomConfirmationBlockReason.ROOM_COMPLETED -> "종료된 룸이에요"
                RoomConfirmationBlockReason.ROOM_CANCELED -> "취소된 룸이에요"
                RoomConfirmationBlockReason.SCHEDULE_PASSED -> "진행 일정이 지났어요"
                RoomConfirmationBlockReason.BELOW_MIN_CAPACITY ->
                    "인원 ${detail.currentParticipants} / ${detail.room.capacity.max}명 " +
                        "(최소 ${detail.room.capacity.min}명)"
            }
            return RoomReadBlockReasonResponse(reason.name, label)
        }
    }
}
