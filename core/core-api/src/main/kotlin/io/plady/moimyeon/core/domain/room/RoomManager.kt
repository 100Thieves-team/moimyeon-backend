package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomManager(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
) {
    // 룸 생성 트랜잭션. ERD §4.2는 room + 방장 participation + resume_submission + chat_room + room_status_log
    // 5개를 한 트랜잭션으로 쓴다. 현재 room 과 방장 participation 까지 구현했고, 나머지 3개는 엔티티가 생기면 추가한다.
    @Transactional
    fun create(room: Room, hostMemberId: UUID, resumeId: UUID) {
        roomRepository.save(RoomMapper.toEntity(room))
        participationRepository.save(
            ParticipationEntity(
                roomId = room.id,
                memberId = hostMemberId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = LocalDateTime.now(),
            ),
        )
        // TODO(BE-05 잔여): resume_submission(resumeId) · chat_room · room_status_log(생성 전이) — 엔티티 생성 필요.
    }

    // 편집 가능한 필드 수정. 방장만 가능. 오프라인 지역 참조 검증은 RoomService 가 트랜잭션 밖에서 한다.
    @Transactional
    fun update(roomId: UUID, hostMemberId: UUID, command: RoomUpdateCommand) {
        val room = loadActiveRoomAsHost(roomId, hostMemberId)

        // 정원 범위는 RoomCapacity 가 보지만 그 값 객체는 DB 를 모른다. 이미 들어와 있는 사람과의 비교는 여기서 한다.
        // 최소 인원은 현재 인원보다 커도 된다 — 확정이 미뤄질 뿐 지금 상태를 깨지 않는다(「진행 확정」§4.3).
        val current = participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED).toInt()
        requireBusiness(command.capacity.max >= current, CoreErrorType.ROOM_CAPACITY_BELOW_PARTICIPANTS)

        val (meetingType, sigunguId) = command.meetingPlace.toEntityValues()
        room.update(
            title = command.title.value,
            description = command.description?.value,
            interviewStage = command.interviewStage,
            interviewType = command.interviewType,
            meetingType = meetingType,
            sigunguId = sigunguId,
            minCapacity = command.capacity.min.toShort(),
            maxCapacity = command.capacity.max.toShort(),
            startAt = command.schedule.startAt,
            durationMinutes = command.schedule.durationMinutes.toShort(),
        )
    }

    // 삭제 = 소프트 삭제(deleted_at). 방장만 가능. 그 외 상태·조건 검사는 두지 않는다.
    @Transactional
    fun delete(roomId: UUID, hostMemberId: UUID) {
        val room = loadActiveRoomAsHost(roomId, hostMemberId)
        room.delete(LocalDateTime.now())
    }

    private fun loadActiveRoomAsHost(roomId: UUID, memberId: UUID): RoomEntity {
        val room = requireFound(
            roomRepository.findById(roomId).orElse(null)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireBusiness(
            participationRepository.existsByRoomIdAndMemberIdAndParticipationRoleAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationRole.HOST,
            ),
            CoreErrorType.ROOM_FORBIDDEN,
        )
        return room
    }

    private fun MeetingPlace.toEntityValues(): Pair<MeetingType, Long?> = when (this) {
        MeetingPlace.Online -> MeetingType.ONLINE to null
        is MeetingPlace.Offline -> MeetingType.OFFLINE to sigunguId
    }
}
