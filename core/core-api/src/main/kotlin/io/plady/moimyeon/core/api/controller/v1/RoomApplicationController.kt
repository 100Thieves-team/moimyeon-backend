package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.RejectApplicationRequest
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationDecisionResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationsResponse
import io.plady.moimyeon.core.api.facade.RoomApplicationFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 방장의 참가 신청 관리(「룸 참여」 §4.3·§4.4). 신청 제출은 별도 이슈다.
// 목록은 방장 외 비공개이고(§6), 수락은 정원을 최종 확인한 뒤 참여자로 등록하며(마지막 자리 1건만 성공), 반려는 정원·참여자에 영향이 없다.
@RestController
class RoomApplicationController(
    private val roomApplicationFacade: RoomApplicationFacade,
) {
    // GET /v1/rooms/{roomId}/applications — 방장용 참가 신청 목록(§4.3). 전달 사항은 방장 외 비공개.
    // 이력서 AI 요약(J5)·신청자 직무·활동 정보는 연동 전까지 null 로 내려간다.
    @GetMapping("/v1/rooms/{roomId}/applications")
    fun applications(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<RoomApplicationsResponse> {
        return ApiResponse.success(roomApplicationFacade.getApplications(currentMember.id, roomId))
    }

    // POST /v1/rooms/{roomId}/applications/{applicationId}/accept — 신청 수락(§4.4). 방장만 가능.
    @PostMapping("/v1/rooms/{roomId}/applications/{applicationId}/accept")
    fun accept(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @PathVariable applicationId: Long,
    ): ApiResponse<ApplicationDecisionResponse> {
        return ApiResponse.success(roomApplicationFacade.accept(currentMember.id, roomId, applicationId))
    }

    // POST /v1/rooms/{roomId}/applications/{applicationId}/reject — 신청 반려(§4.4). 사유는 선택. 방장만 가능.
    @PostMapping("/v1/rooms/{roomId}/applications/{applicationId}/reject")
    fun reject(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @PathVariable applicationId: Long,
        @RequestBody(required = false) request: RejectApplicationRequest?,
    ): ApiResponse<ApplicationDecisionResponse> {
        val reason = request?.toReason()
        return ApiResponse.success(roomApplicationFacade.reject(currentMember.id, roomId, applicationId, reason))
    }
}
