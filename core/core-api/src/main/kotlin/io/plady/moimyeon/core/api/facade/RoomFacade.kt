package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.RoomCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomReadResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomCreationCommand
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.domain.room.RoomUpdateCommand
import io.plady.moimyeon.core.domain.roomviewer.RoomViewerService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomFacade(
    private val roomService: RoomService,
    private val roomViewerService: RoomViewerService,
    private val jobPostingService: JobPostingService,
    private val companyService: CompanyService,
    private val catalogService: CatalogService,
) {
    fun create(hostMemberId: UUID, command: RoomCreationCommand): RoomCreatedResponse {
        val result = roomService.createRoom(hostMemberId, command)
        return RoomCreatedResponse(roomId = result.roomId, status = result.status.name)
    }

    fun update(hostMemberId: UUID, roomId: UUID, command: RoomUpdateCommand) {
        roomService.updateRoom(hostMemberId, roomId, command)
    }

    fun cancel(hostMemberId: UUID, roomId: UUID) {
        roomService.cancelRoom(hostMemberId, roomId)
    }

    fun confirm(hostMemberId: UUID, roomId: UUID) {
        roomService.confirmRoom(hostMemberId, roomId)
    }

    // 상세는 판정 없이 사실만 조립한다(MOI-500) — 확정 준비 여부·가능한 행동은 내리지 않고,
    // 강제는 신청·확정 API 가 실행 시점에 한다. 뷰어 사실은 비로그인이면 null 이다.
    //
    // 표시명 조립은 탐색 목록(RoomSearchFacade)과 같은 규칙이다: 회사는 룸이 직접 참조하지 않아
    // room → job_posting → company 2단계로 읽고, 참조가 끊어진 자리(폐기된 공고·직무·시군구)는 null 로 비운다.
    fun getRoom(roomId: UUID, viewerMemberId: UUID?): RoomReadResponse {
        val detail = roomService.getRoom(roomId)
        val jobPosting = jobPostingService.getRefs(setOf(detail.room.jobPostingId)).firstOrNull()
        return RoomReadResponse.from(
            detail = detail,
            jobPosting = jobPosting,
            company = jobPosting?.companyId?.let { companyService.getCompanies(setOf(it)).firstOrNull() },
            jobRole = catalogService.getJobRoles(setOf(detail.room.jobRoleId)).firstOrNull(),
            region = (detail.room.meetingPlace as? MeetingPlace.Offline)
                ?.let { catalogService.getRegionLabels(setOf(it.sigunguId)).firstOrNull() },
            viewer = roomViewerService.getViewer(viewerMemberId, roomId),
        )
    }
}
