package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

// 나가기는 방장도 참여자도 하는 행위라 RoomManager(생성·수정·취소·확정)의 일만은 아니다.
// 패키지는 room 에 둔다 — 위임 대상이 없으면 룸을 취소하는 데까지 간다.
@Component
class RoomLeaveManager(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val clock: Clock,
) {
    // 룸 행 잠금이 나가기끼리를 직렬화한다.
    // ⚠️ 이 잠금이 빠져도 예외가 나지 않는다: 둘이 동시에 "최소 인원 + 1" 을 보고 통과하면
    //    확정된 룸이 최소 밑으로 내려간다. 결과 예외가 없어 테스트로도 드러나지 않는다.
    @Transactional
    fun leave(roomId: UUID, memberId: UUID) {
        val room = loadRoomForUpdate(roomId)
        requireBusiness(room.canLeave(), CoreErrorType.ROOM_ALREADY_CLOSED)

        val participation = requireFound(
            participationRepository.findByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationStatus.JOINED,
            ),
            CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN,
        )
        requireAboveMinCapacity(room)

        participation.leave(LocalDateTime.now(clock), memberId)
    }

    // 확정은 "이 인원으로 진행한다"는 약속이다. 모집 중에는 아직 약속이 없어 보지 않는다.
    private fun requireAboveMinCapacity(room: RoomEntity) {
        if (room.status != RoomStatus.CONFIRMED) return
        val current = participationRepository
            .countByRoomIdAndStatusAndDeletedAtIsNull(room.id, ParticipationStatus.JOINED)
        requireBusiness(current > room.minCapacity, CoreErrorType.ROOM_AT_MIN_CAPACITY)
    }

    private fun loadRoomForUpdate(roomId: UUID): RoomEntity = requireFound(
        roomRepository.findByIdForUpdate(roomId)?.takeIf { it.isActive() },
        CoreErrorType.ROOM_NOT_FOUND,
    )
}
