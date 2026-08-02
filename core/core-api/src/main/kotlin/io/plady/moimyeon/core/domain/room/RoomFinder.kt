package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomFinder(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
) {
    // 룸 단건 조회. 삭제된 룸은 없는 것으로 본다. 현재 인원 = 활성 참여 수, 방장 = HOST 참여 행.
    fun getRoom(roomId: UUID): RoomDetail {
        val entity = requireFound(
            roomRepository.findById(roomId).orElse(null)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        val host = requireFound(
            participationRepository.findFirstByRoomIdAndParticipationRoleAndDeletedAtIsNull(
                roomId,
                ParticipationRole.HOST,
            ),
            CoreErrorType.ROOM_NOT_FOUND,
        )
        val currentParticipants = participationRepository.countByRoomIdAndDeletedAtIsNull(roomId).toInt()
        return RoomDetail(
            room = RoomMapper.toDomain(entity),
            hostMemberId = host.memberId,
            currentParticipants = currentParticipants,
        )
    }
}
