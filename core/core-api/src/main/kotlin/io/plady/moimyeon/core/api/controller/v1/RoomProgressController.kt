package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CompleteRoomRequest
import io.plady.moimyeon.core.api.controller.v1.response.AttendanceResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomProgressCompletionResponse
import io.plady.moimyeon.core.api.facade.RoomProgressFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class RoomProgressController(
    private val progressFacade: RoomProgressFacade,
) {
    @PostMapping("/v1/rooms/{roomId}/complete")
    fun complete(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @RequestBody request: CompleteRoomRequest,
    ): ApiResponse<RoomProgressCompletionResponse> {
        return ApiResponse.success(progressFacade.complete(currentMember.id, roomId, request.toAttendances()))
    }

    @GetMapping("/v1/attendances/me")
    fun myAttendance(
        @LoginMember currentMember: CurrentMember,
        @RequestParam roomId: UUID,
    ): ApiResponse<AttendanceResponse> {
        return ApiResponse.success(progressFacade.getMyAttendance(currentMember.id, roomId))
    }
}
