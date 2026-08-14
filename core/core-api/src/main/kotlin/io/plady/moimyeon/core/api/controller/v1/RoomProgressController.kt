package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.StartRoomProgressRequest
import io.plady.moimyeon.core.api.controller.v1.response.AttendanceResponse
import io.plady.moimyeon.core.api.controller.v1.response.ProgressRailResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomProgressStartResponse
import io.plady.moimyeon.core.api.facade.RoomProgressFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.progress.RoomProgressService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class RoomProgressController(
    private val progressService: RoomProgressService,
    private val progressFacade: RoomProgressFacade,
) {
    @PostMapping("/v1/room-progresses")
    fun start(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: StartRoomProgressRequest,
    ): ApiResponse<RoomProgressStartResponse> {
        val result = progressService.start(currentMember.id, request.roomId, request.toAttendances())
        return ApiResponse.success(RoomProgressStartResponse.from(result))
    }

    @GetMapping("/v1/progress-rails")
    fun rail(
        @LoginMember currentMember: CurrentMember,
        @RequestParam roomId: UUID,
    ): ApiResponse<ProgressRailResponse> {
        return ApiResponse.success(progressFacade.getRail(currentMember.id, roomId))
    }

    @GetMapping("/v1/attendances/me")
    fun myAttendance(
        @LoginMember currentMember: CurrentMember,
        @RequestParam roomId: UUID,
    ): ApiResponse<AttendanceResponse> {
        return ApiResponse.success(
            AttendanceResponse.from(progressService.getMyAttendance(currentMember.id, roomId)),
        )
    }
}
