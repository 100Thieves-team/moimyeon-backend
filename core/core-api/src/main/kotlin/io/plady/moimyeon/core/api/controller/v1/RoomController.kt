package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateRoomRequest
import io.plady.moimyeon.core.api.controller.v1.request.RoomSearchRequest
import io.plady.moimyeon.core.api.controller.v1.request.UpdateRoomRequest
import io.plady.moimyeon.core.api.controller.v1.response.RoomCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomCreationLimitResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomFormOptionsResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomReadResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomsResponse
import io.plady.moimyeon.core.api.facade.RoomFacade
import io.plady.moimyeon.core.api.facade.RoomSearchFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.api.security.OptionalLoginMember
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class RoomController(
    private val roomFacade: RoomFacade,
    private val roomSearchFacade: RoomSearchFacade,
    private val roomService: RoomService,
) {
    @PostMapping("/v1/rooms")
    fun create(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateRoomRequest,
    ): ApiResponse<RoomCreatedResponse> {
        return ApiResponse.success(roomFacade.create(currentMember.id, request.toCommand()))
    }

    @PutMapping("/v1/rooms/{roomId}")
    fun update(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @RequestBody request: UpdateRoomRequest,
    ): ApiResponse<Any> {
        roomFacade.update(currentMember.id, roomId, request.toCommand())
        return ApiResponse.success()
    }

    // 여기서부터 참여자·정보가 고정되고 대기 신청이 일괄 종료된다. 리소스 생성이 아니라 조건부 상태 전이이므로
    // POST를 사용하고, 두 번째 요청은 409로 거부한다.
    @PostMapping("/v1/rooms/{roomId}/confirmation")
    fun confirm(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<Any> {
        roomFacade.confirm(currentMember.id, roomId)
        return ApiResponse.success()
    }

    // literal 경로가 {roomId} 보다 우선 매칭된다.
    @GetMapping("/v1/rooms/form-options")
    fun formOptions(): ApiResponse<RoomFormOptionsResponse> {
        return ApiResponse.success(RoomFormOptionsResponse.of())
    }

    // 회원의 자원이 아니라 룸 생성의 사전 판정이라 /v1/members/me 가 아니라 여기에 둔다.
    @GetMapping("/v1/rooms/creation-limit")
    fun creationLimit(
        @LoginMember currentMember: CurrentMember,
        @RequestParam jobPostingId: Long,
        @RequestParam jobRoleId: Long,
    ): ApiResponse<RoomCreationLimitResponse> {
        return ApiResponse.success(
            RoomCreationLimitResponse.from(roomService.getRoomCreationLimit(currentMember.id, jobPostingId, jobRoleId)),
        )
    }

    @GetMapping("/v1/rooms")
    fun list(
        @OptionalLoginMember currentMember: CurrentMember?,
        request: RoomSearchRequest,
    ): ApiResponse<RoomsResponse> {
        val sort = request.toSort()
        return ApiResponse.success(
            roomSearchFacade.search(
                request.toCondition(),
                sort,
                request.toCursor(sort),
                request.toSize(),
                currentMember?.id,
            ),
        )
    }

    @GetMapping("/v1/rooms/{roomId}")
    fun detail(
        @OptionalLoginMember currentMember: CurrentMember?,
        @PathVariable roomId: UUID,
    ): ApiResponse<RoomReadResponse> {
        return ApiResponse.success(roomFacade.getRoom(roomId, currentMember?.id))
    }
}
