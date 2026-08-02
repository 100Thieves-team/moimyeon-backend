package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.RoomCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomReadResponse
import io.plady.moimyeon.core.domain.room.RoomCreationCommand
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.domain.room.RoomUpdateCommand
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomFacade(
    private val roomService: RoomService,
) {
    fun create(hostMemberId: UUID, command: RoomCreationCommand): RoomCreatedResponse {
        val room = roomService.createRoom(hostMemberId, command)
        return RoomCreatedResponse(roomId = room.id, status = room.status.name)
    }

    fun update(hostMemberId: UUID, roomId: UUID, command: RoomUpdateCommand) {
        roomService.updateRoom(hostMemberId, roomId, command)
    }

    fun delete(hostMemberId: UUID, roomId: UUID) {
        roomService.deleteRoom(hostMemberId, roomId)
    }

    fun getRoom(roomId: UUID): RoomReadResponse = RoomReadResponse.from(roomService.getRoom(roomId))
}
