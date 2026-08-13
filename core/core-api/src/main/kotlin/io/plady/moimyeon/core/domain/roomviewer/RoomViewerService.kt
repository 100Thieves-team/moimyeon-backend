package io.plady.moimyeon.core.domain.roomviewer

import org.springframework.stereotype.Service
import java.util.UUID

// 조회 요청이라 흐름이랄 것이 없다. Reader 로 한 줄 위임한다.
@Service
class RoomViewerService(
    private val roomViewerReader: RoomViewerReader,
) {
    fun getViewers(viewerMemberId: UUID?, rooms: Map<UUID, RoomApplicability>): Map<UUID, RoomViewer> {
        return roomViewerReader.readAll(viewerMemberId, rooms)
    }

    fun getViewer(viewerMemberId: UUID?, roomId: UUID, room: RoomApplicability): RoomViewer {
        return roomViewerReader.readAll(viewerMemberId, mapOf(roomId to room)).getValue(roomId)
    }
}
