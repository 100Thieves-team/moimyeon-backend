package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.RejectApplicationRequest
import io.plady.moimyeon.core.api.controller.v1.request.SubmitRoomApplicationRequest
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationDecisionResponse
import io.plady.moimyeon.core.api.controller.v1.response.MyRoomApplicationResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationSubmittedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationsResponse
import io.plady.moimyeon.core.api.facade.RoomApplicationFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.room.RoomApplicationService
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 참가 신청 제출·조회·철회와 방장의 신청 관리(「룸 참여」 §4.2~§4.4).
// 목록은 방장 외 비공개이고(§6), 수락은 정원을 최종 확인한 뒤 참여자로 등록하며(마지막 자리 1건만 성공), 반려는 정원·참여자에 영향이 없다.
@RestController
class RoomApplicationController(
    private val roomApplicationFacade: RoomApplicationFacade,
    private val roomApplicationService: RoomApplicationService,
    private val roomApplicationSubmissionService: RoomApplicationSubmissionService,
) {
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/v1/rooms/{roomId}/applications")
    fun submit(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @RequestBody request: SubmitRoomApplicationRequest,
    ): ApiResponse<RoomApplicationSubmittedResponse> {
        val applicationId = roomApplicationSubmissionService.submit(currentMember.id, roomId, request.toForm())
        return ApiResponse.success(RoomApplicationSubmittedResponse.of(applicationId))
    }

    @GetMapping("/v1/rooms/{roomId}/applications/me")
    fun myApplication(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<MyRoomApplicationResponse> {
        return ApiResponse.success(
            MyRoomApplicationResponse.from(roomApplicationSubmissionService.getLatestApplication(currentMember.id, roomId)),
        )
    }

    @DeleteMapping("/v1/rooms/{roomId}/applications/me")
    fun withdraw(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<Any> {
        roomApplicationSubmissionService.withdraw(currentMember.id, roomId)
        return ApiResponse.success()
    }

    // GET /v1/rooms/{roomId}/applications — 방장용 참가 신청 목록(§4.3). 전달 사항은 방장 외 비공개.
    // 이력서 AI 요약과 관심 직무는 각 격벽의 실제 데이터를 조립한다. 공개 활동 정보는 신뢰 정보 구현 전까지 null이다.
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
        return ApiResponse.success(
            ApplicationDecisionResponse.from(roomApplicationService.accept(currentMember.id, roomId, applicationId)),
        )
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
        return ApiResponse.success(
            ApplicationDecisionResponse.from(roomApplicationService.reject(currentMember.id, roomId, applicationId, reason)),
        )
    }
}
