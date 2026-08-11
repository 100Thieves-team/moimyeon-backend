package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomManager(
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val clock: Clock,
) {
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
        room.delete(LocalDateTime.now())
    }

    // 방장이 모집을 접는다. 참여자가 있으면 접을 수 없고 나가기(MOI-397)로 넘겨야 한다.
    //
    // 룸 행 잠금이 취소를 수락·신청 제출과 직렬화한다(셋 다 findByIdForUpdate 를 쓴다).
    // ⚠️ 이 잠금이 빠져도 예외가 나지 않는다: 취소된 룸에 대기 신청이 조용히 남고 그 신청자는
    //    대기 한도 한 칸을 영원히 물고 있게 된다. 테스트로 드러나지 않으므로 지우지 않는다.
    @Transactional
    fun cancel(roomId: UUID, hostMemberId: UUID) {
        val room = loadRoomForUpdateAsHost(roomId, hostMemberId)
        requireBusiness(room.canCancel(), CoreErrorType.ROOM_NOT_RECRUITING)
        requireBusiness(!hasParticipant(roomId), CoreErrorType.ROOM_HAS_PARTICIPANTS)

        // 순서를 지킨다. 벌크의 flushAutomatically 가 앞의 두 쓰기를 먼저 내보내고,
        // 이 트랜잭션은 RoomApplicationEntity 를 로드하지 않아 벌크 뒤 컨텍스트를 비울 필요가 없다.
        val now = LocalDateTime.now(clock)
        room.cancel()
        roomStatusLogRepository.save(
            RoomStatusLogEntity(
                roomId = roomId,
                transitionType = RoomStatus.CANCELED,
                handlerMemberId = hostMemberId,
                occurredAt = now,
            ),
        )
        roomApplicationRepository.closeAllPending(roomId, RoomApplicationStatus.ROOM_CANCELED, now)
    }

    // 방장 외 참여자가 남아 있는가. 방장도 참여 행을 갖기 때문에 역할로 좁혀야 한다.
    private fun hasParticipant(roomId: UUID): Boolean {
        return participationRepository.existsByRoomIdAndParticipationRoleAndStatusAndDeletedAtIsNull(
            roomId,
            ParticipationRole.PARTICIPANT,
            ParticipationStatus.JOINED,
        )
    }

    private fun loadRoomForUpdateAsHost(roomId: UUID, memberId: UUID): RoomEntity {
        val room = requireFound(
            roomRepository.findByIdForUpdate(roomId)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireHost(roomId, memberId)
        return room
    }

    private fun loadActiveRoomAsHost(roomId: UUID, memberId: UUID): RoomEntity {
        val room = requireFound(
            roomRepository.findById(roomId).orElse(null)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireHost(roomId, memberId)
        return room
    }

    private fun requireHost(roomId: UUID, memberId: UUID) {
        requireBusiness(
            participationRepository.existsByRoomIdAndMemberIdAndParticipationRoleAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationRole.HOST,
            ),
            CoreErrorType.ROOM_FORBIDDEN,
        )
    }

    private fun MeetingPlace.toEntityValues(): Pair<MeetingType, Long?> = when (this) {
        MeetingPlace.Online -> MeetingType.ONLINE to null
        is MeetingPlace.Offline -> MeetingType.OFFLINE to sigunguId
    }
}
