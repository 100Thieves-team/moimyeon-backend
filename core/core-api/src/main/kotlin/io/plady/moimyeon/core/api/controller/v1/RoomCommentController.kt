package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateRoomCommentRequest
import io.plady.moimyeon.core.api.controller.v1.request.GetRoomCommentsRequest
import io.plady.moimyeon.core.api.controller.v1.response.RoomCommentCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomCommentsResponse
import io.plady.moimyeon.core.api.facade.RoomCommentFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.domain.roomcomment.RoomCommentService
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 룸 방명록(「룸 방명록」, MOI-461). 세 API 모두 룸 참여자만 접근하고 그 외에는 E1419 로 끊는다.
@RestController
class RoomCommentController(
    private val roomCommentFacade: RoomCommentFacade,
    // 조합할 것이 없는 단일 Service 호출은 컨트롤러가 직접 한다(layers.md).
    private val roomCommentService: RoomCommentService,
) {
    // GET /v1/rooms/{roomId}/comments - 글 목록(최신순 keyset 커서) + 작성 가능 여부.
    @GetMapping("/v1/rooms/{roomId}/comments")
    fun comments(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @ModelAttribute request: GetRoomCommentsRequest,
    ): ApiResponse<RoomCommentsResponse> {
        return ApiResponse.success(
            roomCommentFacade.getComments(currentMember.id, roomId, request.toCursor(), request.toSize()),
        )
    }

    // POST /v1/rooms/{roomId}/comments - 글 작성. 201 이 아닌 200: 직전 글과 같은 내용의
    // 재시도(더블클릭)는 새 자원을 만들지 않고 기존 글을 돌려주는 멱등 응답이다.
    @PostMapping("/v1/rooms/{roomId}/comments")
    fun leaveComment(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @RequestBody request: CreateRoomCommentRequest,
    ): ApiResponse<RoomCommentCreatedResponse> {
        return ApiResponse.success(
            roomCommentFacade.leaveComment(currentMember.id, roomId, request.toContent()),
        )
    }

    // DELETE /v1/rooms/{roomId}/comments/{commentId} - 내 글 삭제(소프트). 목록에는 tombstone 으로
    // 남는다. 이미 삭제된 글의 재삭제는 200 - "이미 없는 것"이 목표 상태다.
    @DeleteMapping("/v1/rooms/{roomId}/comments/{commentId}")
    fun deleteComment(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @PathVariable commentId: Long,
    ): ApiResponse<Any> {
        roomCommentService.deleteComment(currentMember.id, roomId, commentId)
        return ApiResponse.success()
    }
}
