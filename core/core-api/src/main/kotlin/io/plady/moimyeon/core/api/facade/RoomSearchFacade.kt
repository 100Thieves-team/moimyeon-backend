package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.request.RoomSearchCursorToken
import io.plady.moimyeon.core.api.controller.v1.response.RoomSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomsResponse
import io.plady.moimyeon.core.domain.catalog.CatalogService
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.RoomCard
import io.plady.moimyeon.core.domain.room.RoomCursor
import io.plady.moimyeon.core.domain.room.RoomSearchCondition
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.domain.room.RoomSortOrder
import org.springframework.stereotype.Component

// 탐색 목록의 표시명 조립 지점. 룸 조회는 room 개념이 끝내고, 여기서는 다른 개념의 이름만 붙인다.
//
// 회사는 룸이 직접 참조하지 않는다(room → job_posting → company). 그래서 공고를 먼저 읽고
// 거기서 얻은 회사 id 로 회사를 읽는 2단계다. 각 단계는 한 페이지 분량의 id 에만 IN 으로 걸어
// 룸 수에 비례해 쿼리가 늘지 않게 한다.
@Component
class RoomSearchFacade(
    private val roomService: RoomService,
    private val jobPostingService: JobPostingService,
    private val companyService: CompanyService,
    private val catalogService: CatalogService,
) {
    fun search(
        condition: RoomSearchCondition,
        sort: RoomSortOrder,
        cursor: RoomCursor?,
        size: Int,
    ): RoomsResponse {
        val page = roomService.searchRooms(condition, sort, cursor, size)

        val jobPostings = jobPostingService.getLabels(page.cards.map { it.room.jobPostingId }.toSet())
            .associateBy { it.id }
        val companies = companyService.getCompanies(jobPostings.values.mapNotNull { it.companyId }.toSet())
            .associateBy { it.id }
        val jobRoles = catalogService.getJobRoles(page.cards.map { it.room.jobRoleId }.toSet())
            .associateBy { it.id }
        val regions = catalogService.getRegionLabels(page.cards.mapNotNull { it.offlineSigunguId() }.toSet())
            .associateBy { it.sigunguId }

        return RoomsResponse(
            rooms = page.cards.map { card ->
                val jobPosting = jobPostings[card.room.jobPostingId]
                RoomSummaryResponse.from(
                    card = card,
                    jobPosting = jobPosting,
                    company = jobPosting?.companyId?.let { companies[it] },
                    jobRole = jobRoles[card.room.jobRoleId],
                    region = card.offlineSigunguId()?.let { regions[it] },
                )
            },
            sort = sort.name,
            totalCount = page.totalCount.toInt(),
            nextCursor = page.nextCursor?.let { RoomSearchCursorToken.encode(it, sort) },
        )
    }

    private fun RoomCard.offlineSigunguId(): Long? = (room.meetingPlace as? MeetingPlace.Offline)?.sigunguId
}
