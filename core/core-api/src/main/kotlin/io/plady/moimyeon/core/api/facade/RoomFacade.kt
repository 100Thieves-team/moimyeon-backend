package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.RoomCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomReadResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomConfirmation
import io.plady.moimyeon.core.domain.room.RoomCreationCommand
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.domain.room.RoomUpdateCommand
import io.plady.moimyeon.core.domain.roomviewer.RoomApplicability
import io.plady.moimyeon.core.domain.roomviewer.RoomViewerService
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomFacade(
    private val roomService: RoomService,
    private val roomViewerService: RoomViewerService,
    private val jobPostingService: JobPostingService,
    private val companyService: CompanyService,
    private val catalogService: CatalogService,
    private val clock: Clock,
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

    // 확정 조건은 이미 조회한 룸 상세에서 파생한다. 판정 자체는 도메인이 갖고 여기서는 호출만 한다.
    // 뷰어 관계는 축이 달라(룸의 사실 vs 이 사람이 할 수 있는 것) 별도 개념이 답한다 — 비로그인이면 null 이다.
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
            confirmation = RoomConfirmation.of(detail, LocalDateTime.now(clock)),
            viewer = roomViewerService.getViewer(viewerMemberId, roomId, RoomApplicability.of(detail)),
        )
    }
}
