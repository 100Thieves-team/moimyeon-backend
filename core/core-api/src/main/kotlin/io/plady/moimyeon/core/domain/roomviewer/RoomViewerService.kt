package io.plady.moimyeon.core.domain.roomviewer

import org.springframework.stereotype.Service
import java.util.UUID

// 조회 요청이라 흐름이랄 것이 없다. Reader 로 한 줄 위임한다.
@Service
class RoomViewerService(
    private val roomViewerReader: RoomViewerReader,
) {
    fun getViewers(viewerMemberId: UUID?, roomIds: Collection<UUID>): Map<UUID, ViewerFacts?> {
        return roomViewerReader.readAll(viewerMemberId, roomIds)
    }

    fun getViewer(viewerMemberId: UUID?, roomId: UUID): ViewerFacts? {
        return roomViewerReader.readAll(viewerMemberId, setOf(roomId)).getValue(roomId)
    }
}
