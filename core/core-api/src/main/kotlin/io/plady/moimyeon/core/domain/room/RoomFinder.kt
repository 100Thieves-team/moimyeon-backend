package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class RoomFinder(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
) {
    @Transactional(readOnly = true)
    fun getSummaries(roomIds: Collection<UUID>): List<RoomSummary> {
        return readSummaries(roomIds)
    }

    @Transactional(readOnly = true)
    fun getSummariesByStatus(roomIds: Collection<UUID>): RoomSummariesByStatus {
        val summaries = readSummaries(roomIds)
        return RoomSummariesByStatus(
            active = summaries
                .filter { it.room.status in ACTIVE_ROOM_STATUSES }
                .sortedWith(compareBy<RoomSummary> { it.room.schedule.startAt }.thenBy { it.room.id }),
            completed = summaries
                .filter { it.room.status == RoomStatus.COMPLETED }
                .sortedWith(
                    compareByDescending<RoomSummary> { it.room.schedule.startAt }
                        .thenByDescending { it.room.id },
                ),
        )
    }

    private fun readSummaries(roomIds: Collection<UUID>): List<RoomSummary> {
        if (roomIds.isEmpty()) return emptyList()

        val roomsById = roomRepository.findByIdInAndDeletedAtIsNull(roomIds).associateBy { it.id }
        val participantsByRoomId = participationRepository.countActiveByRoomIds(roomIds)
            .associate { it.roomId to Math.toIntExact(it.count) }

        return roomIds.distinct().mapNotNull { roomId ->
            roomsById[roomId]?.let { room ->
                RoomSummary(
                    room = RoomMapper.toDomain(room),
                    participantCount = participantsByRoomId[roomId] ?: 0,
                )
            }
        }
    }

    private companion object {
        val ACTIVE_ROOM_STATUSES = setOf(
            RoomStatus.RECRUITING,
            RoomStatus.CONFIRMED,
            RoomStatus.IN_PROGRESS,
        )
    }

    // 룸 단건 조회. 삭제된 룸은 없는 것으로 본다. 방장 = HOST 참여 행.
    // 현재 인원은 참여 중(JOINED)인 사람만 센다 — 나간 사람의 자리는 비워져 다시 채울 수 있어야 한다.
    // 이 술어는 정원 확정(RoomApplicationManager)·탐색 목록과 반드시 같아야 한다. 갈리면 목록에서는
    // 자리가 있어 보이는데 수락 단계에서 정원 초과가 나는 상태가 된다.
    fun getDetail(roomId: UUID): RoomDetail {
        val entity = requireFound(
            roomRepository.findById(roomId).orElse(null)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        val host = requireFound(
            participationRepository.findFirstByRoomIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
                roomId,
                ParticipationRole.HOST,
                ParticipationStatus.JOINED,
            ),
            CoreErrorType.ROOM_NOT_FOUND,
        )
        val currentParticipants = participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED).toInt()
        // 대기 신청 수는 수만 공개한다(「룸 참여」 §4.1·§6). 대기자 목록과 신청 내용은 방장 외 비공개다.
        val pendingApplicationCount = roomApplicationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(
            roomId,
            RoomApplicationStatus.PENDING,
        ).toInt()
        return RoomDetail(
            room = RoomMapper.toDomain(entity),
            hostMemberId = host.memberId,
            currentParticipants = currentParticipants,
            pendingApplicationCount = pendingApplicationCount,
        )
    }

    // 룸 자체만 필요한 경로용. 인원·대기 수 집계를 건너뛴다 - 상태나 공개 정책만 보는 호출부가 여럿이다.
    fun getRoom(roomId: UUID): Room {
        val entity = requireFound(
            roomRepository.findById(roomId).orElse(null)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        return RoomMapper.toDomain(entity)
    }

    fun getAllByIds(roomIds: Collection<UUID>): List<Room> {
        if (roomIds.isEmpty()) return emptyList()
        return roomRepository.findAllById(roomIds)
            .filter { it.isActive() }
            .map(RoomMapper::toDomain)
    }
}
