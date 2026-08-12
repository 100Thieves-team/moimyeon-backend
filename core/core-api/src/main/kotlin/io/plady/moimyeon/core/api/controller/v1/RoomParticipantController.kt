package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.response.RoomParticipantsResponse
import io.plady.moimyeon.core.api.facade.RoomParticipantFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
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

    // DELETE /v1/rooms/{roomId}/participants/me — 참여자가 스스로 명단에서 빠진다(「룸 참여」 §4.6).
    // 취소·확정과 달리 DELETE 인 이유: 이건 상태 전이가 아니라 컬렉션에서 내 참여를 지우는 것이다.
    // 두 번째 요청이 E1419 로 거부되는 것은 "이미 없는 것을 지울 수 없다"는 뜻이라 DELETE 와 어긋나지 않는다.
    @DeleteMapping("/v1/rooms/{roomId}/participants/me")
    fun leave(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<Any> {
        roomParticipantFacade.leave(currentMember.id, roomId)
        return ApiResponse.success()
    }
}
