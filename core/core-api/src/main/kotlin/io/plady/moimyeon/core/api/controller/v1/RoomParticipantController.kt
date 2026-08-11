package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.RoomParticipantsResponse
import io.plady.moimyeon.core.api.facade.RoomParticipantFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class RoomParticipantController(
    private val roomParticipantFacade: RoomParticipantFacade,
) {
    // GET /v1/rooms/{roomId}/participants — 참여자 명부(「룸 참여」 §4.5).
    // 방장·참여자만 조회한다(E1419). 취소·종료된 룸에서도 이미 속한 사람에게는 진입점이 유지된다.
    @GetMapping("/v1/rooms/{roomId}/participants")
    fun participants(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<RoomParticipantsResponse> {
        return ApiResponse.success(roomParticipantFacade.getParticipants(currentMember.id, roomId))
    }
}
