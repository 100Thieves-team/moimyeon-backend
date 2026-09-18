package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.roomviewer.ViewerFacts

// 조회자 "본인"에 대한 사실만 싣는다(MOI-500 — MOI-387 판정 계약의 재설계).
// 목록과 상세가 같은 객체를 싣는다 — 클라이언트가 판정 함수를 하나만 만들면 된다.
//
// 판정 결과(relation·actions·blockReason)는 내리지 않는다. 버튼·배지 판정은 화면 소관이고,
// 신청 가능 여부의 강제와 사유 안내는 신청 API 의 에러 응답이 전담한다
// (E1002 제재 | E1410 모집 아님 | E1412 일정 경과 | E1413 중복 | E1415 반려·강퇴 재신청 |
//  E1416 대기 한도 | E1425 참여 슬롯). 비로그인이면 이 객체 자체가 null 이다.
data class RoomViewerResponse(
    val isHost: Boolean,
    // 방장도 참여자(JOINED)라 방장이면 둘 다 true 다 — 화면은 isHost 를 먼저 본다.
    val isParticipating: Boolean,
    // 방장이 내보낸 이력. 자진 이탈은 포함하지 않는다 — 재신청을 막는 것은 강퇴뿐이다.
    // 강퇴자의 신청은 ACCEPTED 로 남으므로 latestApplicationStatus 보다 먼저 봐야 한다.
    val hasRemovalHistory: Boolean,
    val latestApplicationStatus: String?, // RoomApplicationStatus, 신청 이력 없으면 null
    val member: ViewerMemberResponse,
) {
    companion object {
        fun from(facts: ViewerFacts): RoomViewerResponse = RoomViewerResponse(
            isHost = facts.room.host,
            isParticipating = facts.room.participating,
            hasRemovalHistory = facts.room.removed,
            latestApplicationStatus = facts.room.latestApplication?.name,
            member = ViewerMemberResponse(
                isActive = facts.member.active,
                participationSlots = ViewerQuotaResponse(
                    occupied = facts.member.participationSlots.occupied,
                    limit = facts.member.participationSlots.limit,
                ),
                pendingApplicationQuota = ViewerQuotaResponse(
                    occupied = facts.member.pendingApplicationQuota.occupied,
                    limit = facts.member.pendingApplicationQuota.limit,
                ),
            ),
        )
    }
}

// 룸과 무관한 회원 축 사실.
data class ViewerMemberResponse(
    val isActive: Boolean,
    val participationSlots: ViewerQuotaResponse,
    val pendingApplicationQuota: ViewerQuotaResponse,
)

// 사용량은 occupied·limit 만 — remaining 같은 파생값은 내리지 않는다(D10).
data class ViewerQuotaResponse(
    val occupied: Long,
    val limit: Int,
)
