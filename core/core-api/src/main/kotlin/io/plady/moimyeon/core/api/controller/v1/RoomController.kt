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
    // 조합할 것이 없는 단일 Service 호출은 컨트롤러가 직접 한다(layers.md).
    private val roomService: RoomService,
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

    // POST /v1/rooms/{roomId}/cancellation — 방장이 모집을 접는다. 방장만 가능.
    // DELETE 가 아닌 이유: 취소는 리소스 제거가 아니라 상태 전이이고, 참여자가 있으면 거부되는
    // 조건부 연산이라 DELETE 의 멱등 기대와 맞지 않는다. 운영이 룸을 내리는 소프트 삭제는 별개다.
    @PostMapping("/v1/rooms/{roomId}/cancellation")
    fun cancel(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<Any> {
        roomFacade.cancel(currentMember.id, roomId)
        return ApiResponse.success()
    }

    // POST /v1/rooms/{roomId}/confirmation — 방장이 진행을 확정한다(「진행 확정」 §4.2).
    // 여기서부터 참여자·정보가 고정되고 대기 신청이 일괄 종료된다. 취소와 같은 이유로 POST 다 —
    // 리소스 생성이 아니라 조건부 상태 전이이고, 두 번째 요청은 409 로 거부된다.
    @PostMapping("/v1/rooms/{roomId}/confirmation")
    fun confirm(
        @LoginMember currentMember: CurrentMember,
        @PathVariable roomId: UUID,
    ): ApiResponse<Any> {
        roomFacade.confirm(currentMember.id, roomId)
        return ApiResponse.success()
    }

    // GET /v1/rooms/form-options — 폼 선택지(literal 경로가 {roomId} 보다 우선 매칭됨).
    @GetMapping("/v1/rooms/form-options")
    fun formOptions(): ApiResponse<RoomFormOptionsResponse> {
        return ApiResponse.success(RoomFormOptionsResponse.of())
    }

    // GET /v1/rooms/creation-limit — 같은 공고·직무로 내가 만든 활성 룸이 몇 개인지(「룸 생성」 §4.7).
    // 회원의 자원이 아니라 룸 생성의 사전 판정이라 /v1/members/me 가 아니라 여기에 둔다.
    // 없는 공고·직무 id 도 0개로 답한다 — 참조 검증은 생성 시점의 일이다(RoomFinder 주석).
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

    // GET /v1/rooms — 탐색 목록(「룸 탐색」 §4.1~§4.3). 비로그인도 조회 가능.
    // 잘못된 필터·정렬 값은 그 값만 무시하고 나머지 조건으로 조회한다(§4.7). 반대로 깨진 커서와
    // 앞뒤가 뒤집힌 조회 범위는 400 이다 — 무시하면 순회 자체가 망가지거나 무엇을 무시할지 정할 수 없다.
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

    // GET /v1/rooms/{roomId} — 룸 단건 조회. 룸의 실제 저장 데이터 + 현재 인원 + 방장 식별자를 반환한다.
    // 회사·공고·직무 표시명, 방장 프로필/신뢰 지표, 탐색 목록 enrich 는 별도 이슈다(docs/room-progress.md).
    @GetMapping("/v1/rooms/{roomId}")
    fun detail(
        @OptionalLoginMember currentMember: CurrentMember?,
        @PathVariable roomId: UUID,
    ): ApiResponse<RoomReadResponse> {
        return ApiResponse.success(roomFacade.getRoom(roomId, currentMember?.id))
    }
}
