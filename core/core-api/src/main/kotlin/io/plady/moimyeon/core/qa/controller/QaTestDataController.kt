package io.plady.moimyeon.core.qa.controller

import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.api.auth.DevAccessTokenIssuer
import io.plady.moimyeon.core.qa.QaTestDataService
import io.plady.moimyeon.core.qa.controller.request.CompleteQaResumeSummaryRequest
import io.plady.moimyeon.core.qa.controller.request.QaDataRequest
import io.plady.moimyeon.core.qa.controller.request.RescheduleQaRoomRequest
import io.plady.moimyeon.core.qa.controller.response.QaDataResponse
import io.plady.moimyeon.core.qa.controller.response.QaDeletedResponse
import io.plady.moimyeon.core.qa.controller.response.QaMemberResponse
import io.plady.moimyeon.core.qa.controller.response.QaResumeSummaryResponse
import io.plady.moimyeon.core.qa.controller.response.QaRoomScheduleResponse
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaTestDataController(
    private val qaTestDataService: QaTestDataService,
    private val devAccessTokenIssuer: DevAccessTokenIssuer,
) {
    @GetMapping("/v1/dev/qa-data")
    fun list(request: QaDataRequest): ApiResponse<QaDataResponse> {
        return ApiResponse.success(QaDataResponse.from(qaTestDataService.getRooms(request.toCondition())))
    }

    @DeleteMapping("/v1/dev/qa-data")
    fun deleteAll(request: QaDataRequest): ApiResponse<QaDeletedResponse> {
        return ApiResponse.success(QaDeletedResponse.from(qaTestDataService.deleteRooms(request.toCondition())))
    }

    @DeleteMapping("/v1/dev/rooms/{roomId}")
    fun deleteRoom(
        @PathVariable roomId: UUID,
    ): ApiResponse<QaDeletedResponse> {
        return ApiResponse.success(QaDeletedResponse.from(qaTestDataService.deleteRoom(roomId)))
    }

    @PostMapping("/v1/dev/rooms/{roomId}/schedule")
    fun rescheduleRoom(
        @PathVariable roomId: UUID,
        @RequestBody request: RescheduleQaRoomRequest,
    ): ApiResponse<QaRoomScheduleResponse> {
        return ApiResponse.success(QaRoomScheduleResponse.from(qaTestDataService.rescheduleRoom(roomId, request.toStartAt())))
    }

    @PostMapping("/v1/dev/members")
    fun createMember(): ApiResponse<QaMemberResponse> {
        val member = qaTestDataService.createMember()
        return ApiResponse.success(QaMemberResponse.from(member, devAccessTokenIssuer.issue(member.id)))
    }

    @PostMapping("/v1/dev/members/{memberId}/reset")
    fun resetMember(
        @PathVariable memberId: UUID,
    ): ApiResponse<QaDeletedResponse> {
        return ApiResponse.success(QaDeletedResponse.from(qaTestDataService.resetMember(memberId)))
    }

    @PostMapping("/v1/dev/resumes/{resumeId}/summary")
    fun completeResumeSummary(
        @PathVariable resumeId: UUID,
        @RequestBody(required = false) request: CompleteQaResumeSummaryRequest?,
    ): ApiResponse<QaResumeSummaryResponse> {
        val summary = request?.toSummary() ?: CompleteQaResumeSummaryRequest.DEFAULT_SUMMARY
        return ApiResponse.success(QaResumeSummaryResponse.from(qaTestDataService.completeResumeSummary(resumeId, summary)))
    }
}
