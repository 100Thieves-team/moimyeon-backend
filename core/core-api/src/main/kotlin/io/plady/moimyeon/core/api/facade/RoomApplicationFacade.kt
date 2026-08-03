package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.ApplicantResponse
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationDecisionResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationsResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRecruitSummaryResponse
import io.plady.moimyeon.core.domain.room.ApplicationDecision
import io.plady.moimyeon.core.domain.room.RoomApplicationService
import io.plady.moimyeon.core.domain.room.RoomApplicationView
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomApplicationFacade(
    private val roomApplicationService: RoomApplicationService,
) {
    fun getApplications(requesterMemberId: UUID, roomId: UUID): RoomApplicationsResponse = RoomApplicationsResponse(
        applications = roomApplicationService.getApplications(requesterMemberId, roomId).map { it.toResponse() },
    )

    fun accept(hostMemberId: UUID, roomId: UUID, applicationId: Long): ApplicationDecisionResponse = roomApplicationService.accept(hostMemberId, roomId, applicationId).toResponse()

    fun reject(hostMemberId: UUID, roomId: UUID, applicationId: Long, reason: String?): ApplicationDecisionResponse = roomApplicationService.reject(hostMemberId, roomId, applicationId, reason).toResponse()

    // 모집 상태는 정원 충족 여부로 계산한다(저장값 아님). 라벨은 서버가 소유한다.
    private fun ApplicationDecision.toResponse(): ApplicationDecisionResponse {
        val closed = currentParticipants >= maxCapacity
        return ApplicationDecisionResponse(
            applicationId = applicationId,
            status = status.name,
            statusLabel = status.label(),
            recruit = RoomRecruitSummaryResponse(
                current = currentParticipants,
                max = maxCapacity,
                recruitStatus = if (closed) "CLOSED" else "RECRUITING",
                recruitStatusLabel = if (closed) "모집 마감" else "모집 중",
            ),
        )
    }

    // 직무·활동 정보(trust 격벽)와 이력서 AI 요약(J5)은 아직 원천이 없어 null 로 내려간다.
    private fun RoomApplicationView.toResponse(): RoomApplicationResponse = RoomApplicationResponse(
        applicationId = applicationId,
        applicant = ApplicantResponse(
            memberId = applicantMemberId,
            nickname = applicantNickname,
            jobTitle = null,
            activitySummary = null,
        ),
        note = note,
        aiSummary = null,
        status = status.name,
        statusLabel = status.label(),
        appliedAt = appliedAt,
    )

    private fun RoomApplicationStatus.label(): String = when (this) {
        RoomApplicationStatus.PENDING -> "대기"
        RoomApplicationStatus.ACCEPTED -> "수락"
        RoomApplicationStatus.REJECTED -> "반려"
        RoomApplicationStatus.WITHDRAWN -> "철회"
    }
}
