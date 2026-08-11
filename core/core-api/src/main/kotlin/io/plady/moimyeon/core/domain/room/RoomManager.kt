package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomManager(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val clock: Clock,
) {
    // 한 번의 생성에서 쓰는 시각은 한 번만 찍어 나눠 쓴다. 자리마다 now() 를 부르면 미세하게 갈린다.
    @Transactional
    fun create(room: Room, hostMemberId: UUID, resumeId: UUID) {
        val now = LocalDateTime.now(clock)

        roomRepository.save(RoomMapper.toEntity(room))
        participationRepository.save(
            ParticipationEntity(
                roomId = room.id,
                memberId = hostMemberId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
        // TODO(BE-05 잔여): resume_submission(resumeId) · chat_room · room_status_log(생성 전이) — 엔티티 생성 필요.
    }

    // 편집 가능한 필드 수정. 방장만 가능. 오프라인 지역 참조 검증은 RoomService 가 트랜잭션 밖에서 한다.
    @Transactional
    fun update(roomId: UUID, hostMemberId: UUID, command: RoomUpdateCommand) {
        val room = loadActiveRoomAsHost(roomId, hostMemberId)

        // 확정 이후 변경은 CS 문의로만 푼다(「진행 확정」§4.3).
        requireBusiness(room.status == RoomStatus.RECRUITING, CoreErrorType.ROOM_NOT_EDITABLE)

        // RoomCapacity 는 DB 를 모르는 값 객체라 현재 인원과의 비교는 여기서 한다.
        // 최소 인원은 막지 않는다 — 확정이 미뤄질 뿐 지금 상태를 깨지 않는다.
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
        room.delete(LocalDateTime.now(clock))
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
