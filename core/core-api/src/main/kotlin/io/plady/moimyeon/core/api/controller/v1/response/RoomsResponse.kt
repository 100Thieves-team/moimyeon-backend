package io.plady.moimyeon.core.api.controller.v1.response

import java.util.UUID

// 룸 탐색 목록(GET /v1/rooms) — 「룸 탐색」 §4.1·§4.3. 완료·취소·일정 경과 룸은 제외된다.
// 필터는 AND 결합, 정렬은 요청한 sort 를 그대로 되돌려준다(목은 필터·정렬을 실제 적용하지 않고 고정 목록을 반환).
data class RoomsResponse(
    val rooms: List<RoomSummaryResponse>,
    val sort: String, // SCHEDULE | RECENT | CLOSING (요청 정렬 에코)
    val totalCount: Int,
)

// 목록 카드 한 건. 상세보다 가벼운 공개 정보만 담는다(오프라인 상세 주소·이력서 등 민감 정보 제외, §4.4).
data class RoomSummaryResponse(
    val roomId: UUID,
    val title: String,
    val company: CompanyResponse,
    val jobPosting: RoomJobPostingResponse?,
    val jobRole: JobRoleResponse,
    val round: String,
    val roundLabel: String,
    val type: String?,
    val typeLabel: String?,
    val method: String,
    val methodLabel: String,
    val region: RoomRegionResponse?,
    val schedule: RoomScheduleResponse,
    val recruit: RoomRecruitSummaryResponse,
)

// 목록·수락/반려 응답이 공유하는 경량 모집 현황. 모집 중/마감은 저장값이 아니라 정원 충족 여부로 계산된 값이다(Y3 핵심 결정).
data class RoomRecruitSummaryResponse(
    val current: Int,
    val max: Int,
    val recruitStatus: String, // RECRUITING | CLOSED (정원 충족 시 CLOSED)
    val recruitStatusLabel: String, // 모집 중 | 모집 마감
)
