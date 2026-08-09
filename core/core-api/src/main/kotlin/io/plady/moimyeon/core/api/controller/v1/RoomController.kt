package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.v1.request.CreateRoomRequest
import io.plady.moimyeon.core.api.controller.v1.request.RoomSearchRequest
import io.plady.moimyeon.core.api.controller.v1.request.UpdateRoomRequest
import io.plady.moimyeon.core.api.controller.v1.response.RoomCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomFormOptionsResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomReadResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomsResponse
import io.plady.moimyeon.core.api.facade.RoomFacade
import io.plady.moimyeon.core.api.facade.RoomSearchFacade
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.core.support.response.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class RoomController(
    private val roomFacade: RoomFacade,
    private val roomSearchFacade: RoomSearchFacade,
) {
    // POST /v1/rooms — 생성 전 확인의 '이대로 룸 만들기'. 생성 즉시 모집(RECRUITING) 상태로 등록(§4.8).
    @PostMapping("/v1/rooms")
    fun create(
        @LoginMember currentMember: CurrentMember,
        @RequestBody request: CreateRoomRequest,
    ): ApiResponse<RoomCreatedResponse> {
        return ApiResponse.success(roomFacade.create(currentMember.id, request.toCommand()))
    }

    // PUT /v1/rooms/{roomId} — 방장이 생성 이후 편집 가능한 정보를 수정한다(모집중 상태 편집). 방장만 가능.
    @PutMapping("/v1/rooms/{roomId}")
    fun update(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
        @RequestBody request: UpdateRoomRequest,
    ): ApiResponse<Any> {
        roomFacade.update(currentMember.id, roomId, request.toCommand())
        return ApiResponse.success()
    }

    // DELETE /v1/rooms/{roomId} — 방장이 룸을 삭제(소프트 삭제)한다. 방장만 가능.
    @DeleteMapping("/v1/rooms/{roomId}")
    fun delete(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<Any> {
        roomFacade.delete(currentMember.id, roomId)
        return ApiResponse.success()
    }

    // GET /v1/rooms/form-options — 폼 선택지(literal 경로가 {roomId} 보다 우선 매칭됨).
    @GetMapping("/v1/rooms/form-options")
    fun formOptions(): ApiResponse<RoomFormOptionsResponse> {
        return ApiResponse.success(RoomFormOptionsResponse.mock())
    }

    // GET /v1/rooms — 탐색 목록(「룸 탐색」 §4.1~§4.3). 비로그인도 조회 가능.
    // 잘못된 필터·정렬 값은 그 값만 무시하고 나머지 조건으로 조회한다(§4.7). 반대로 깨진 커서와
    // 앞뒤가 뒤집힌 조회 범위는 400 이다 — 무시하면 순회 자체가 망가지거나 무엇을 무시할지 정할 수 없다.
    @GetMapping("/v1/rooms")
    fun list(request: RoomSearchRequest): ApiResponse<RoomsResponse> {
        val sort = request.toSort()
        return ApiResponse.success(
            roomSearchFacade.search(request.toCondition(), sort, request.toCursor(sort), request.toSize()),
        )
    }

    // GET /v1/rooms/{roomId} — 룸 단건 조회. 룸의 실제 저장 데이터 + 현재 인원 + 방장 식별자를 반환한다.
    // 회사·공고·직무 표시명, 방장 프로필/신뢰 지표, 탐색 목록 enrich 는 별도 이슈다(docs/room-progress.md).
    @GetMapping("/v1/rooms/{roomId}")
    fun detail(
        @PathVariable roomId: UUID,
    ): ApiResponse<RoomReadResponse> {
        return ApiResponse.success(roomFacade.getRoom(roomId))
    }
}
