package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.RoomCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomReadResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.participation.RoomParticipantService
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
    private val roomParticipantService: RoomParticipantService,
    private val memberService: MemberService,
) {
    fun create(hostMemberId: UUID, command: RoomCreationCommand): RoomCreatedResponse {
        val result = roomService.createRoom(hostMemberId, command)
        return RoomCreatedResponse(roomId = result.roomId, status = result.status.name)
    }

    fun update(hostMemberId: UUID, roomId: UUID, command: RoomUpdateCommand) {
        roomService.updateRoom(hostMemberId, roomId, command)
    }

    fun confirm(hostMemberId: UUID, roomId: UUID) {
        roomService.confirmRoom(hostMemberId, roomId)
    }

    fun getRoom(roomId: UUID, viewerMemberId: UUID?): RoomReadResponse {
        val detail = roomService.getRoom(roomId)
        val jobPosting = jobPostingService.getRefs(setOf(detail.room.jobPostingId)).firstOrNull()
        // 참여자 공개 명단(MOI-504). 명부 API(방장·참여자 전용, 이력서 실림)와 달리 공개 데이터(§6)인
        // 닉네임까지만 싣는다 — 방명록 뱃지와 같은 조회(getJoinedParticipants)를 재사용한다.
        val joinedParticipants = roomParticipantService.getJoinedParticipants(roomId)
        val nicknames = memberService.getMembers(joinedParticipants.map { it.memberId })
            .associate { it.id to it.nickname.value }
        return RoomReadResponse.from(
            detail = detail,
            jobPosting = jobPosting,
            company = jobPosting?.companyId?.let { companyService.getCompanies(setOf(it)).firstOrNull() },
            jobRole = catalogService.getJobRoles(setOf(detail.room.jobRoleId)).firstOrNull(),
            region = (detail.room.meetingPlace as? MeetingPlace.Offline)
                ?.let { catalogService.getRegionLabels(setOf(it.sigunguId)).firstOrNull() },
            joinedParticipants = joinedParticipants,
            nicknames = nicknames,
            viewer = roomViewerService.getViewer(viewerMemberId, roomId),
        )
    }
}
