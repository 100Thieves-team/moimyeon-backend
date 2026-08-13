package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.RoomCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomReadResponse
import io.plady.moimyeon.core.domain.room.RoomConfirmation
import io.plady.moimyeon.core.domain.room.RoomCreationCommand
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.domain.room.RoomUpdateCommand
import io.plady.moimyeon.core.domain.roomviewer.RoomApplicability
import io.plady.moimyeon.core.domain.roomviewer.RoomViewerService
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomFacade(
    private val roomService: RoomService,
    private val roomViewerService: RoomViewerService,
    private val clock: Clock,
) {
    fun create(hostMemberId: UUID, command: RoomCreationCommand): RoomCreatedResponse {
        val room = roomService.createRoom(hostMemberId, command)
        return RoomCreatedResponse(roomId = room.id, status = room.status.name)
    }

    fun update(hostMemberId: UUID, roomId: UUID, command: RoomUpdateCommand) {
        roomService.updateRoom(hostMemberId, roomId, command)
    }

    fun cancel(hostMemberId: UUID, roomId: UUID) {
        roomService.cancelRoom(hostMemberId, roomId)
    }

    fun confirm(hostMemberId: UUID, roomId: UUID) {
        roomService.confirmRoom(hostMemberId, roomId)
    }

    // 확정 조건은 이미 조회한 룸 상세에서 파생한다. 판정 자체는 도메인이 갖고 여기서는 호출만 한다.
    // 뷰어 관계는 축이 달라(룸의 사실 vs 이 사람이 할 수 있는 것) 별도 개념이 답한다 — 비로그인이면 null 이다.
    fun getRoom(roomId: UUID, viewerMemberId: UUID?): RoomReadResponse {
        val detail = roomService.getRoom(roomId)
        return RoomReadResponse.from(
            detail,
            RoomConfirmation.of(detail, LocalDateTime.now(clock)),
            roomViewerService.getViewer(viewerMemberId, roomId, RoomApplicability.of(detail)),
        )
    }
}
