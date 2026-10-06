package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.RejectApplicationRequest
import io.plady.moimyeon.core.api.controller.v1.request.SubmitRoomApplicationRequest
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationDecisionResponse
import io.plady.moimyeon.core.api.controller.v1.response.MyRoomApplicationResponse
import io.plady.moimyeon.core.api.controller.v1.response.RejectReasonsResponse
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

    @GetMapping("/v1/rooms/{roomId}/applications")
    fun applications(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<RoomApplicationsResponse> {
        return ApiResponse.success(roomApplicationFacade.getApplications(currentMember.id, roomId))
    }

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

    // literal 경로가 {roomId} 보다 우선 매칭된다.
    // 정적 카탈로그라 도메인 판정이 없어 Service 를 거치지 않는다(form-options 와 같은 이유).
    @GetMapping("/v1/rooms/reject-reasons")
    fun rejectReasons(): ApiResponse<RejectReasonsResponse> {
        return ApiResponse.success(RejectReasonsResponse.of())
    }

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
