package io.plady.moimyeon.core.domain.roomcomment

import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

// 방명록의 작성 가능 여부는 룸의 생명주기에서 파생된다 - 판정이 개념 경계를 넘으므로 별도 도구다.
// 룸은 로직 클래스(RoomFinder)에 위임하고, 전이 로그는 노출된 로직 클래스가 없어 Repository 를
// 직접 본다(RoomProgressManager 와 같은 형태). 삭제된 룸은 getRoom 이 E1405 로 끊는다(명부와 같은 태도).
@Component
class RoomCommentWindowReader(
    private val roomFinder: RoomFinder,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) {
    fun getWindow(roomId: UUID, now: LocalDateTime): RoomCommentWindow {
        val room = roomFinder.getRoom(roomId)
        val terminalTransitionAt = when (room.status) {
            RoomStatus.CANCELED, RoomStatus.COMPLETED ->
                roomStatusLogRepository
                    .findByRoomIdAndTransitionTypeAndDeletedAtIsNull(roomId, room.status)
                    ?.occurredAt

            else -> null
        }
        return RoomCommentWindow.of(room.status, terminalTransitionAt, now)
    }
}
