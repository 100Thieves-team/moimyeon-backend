package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoomApplicationManager(
    private val roomRepository: RoomRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val participationRepository: ParticipationRepository,
) {
    // 수락. 룸 행에 쓰기 잠금을 걸어 동시 수락을 직렬화한 뒤(마지막 자리 1건만 성공, §4.4),
    // 방장 권한·신청 상태·모집 여부·정원을 확인하고 신청자를 참여자로 등록한다.
    @Transactional
    fun accept(roomId: UUID, applicationId: Long, hostMemberId: UUID): ApplicationDecision {
        val room = loadRoomForUpdateAsHost(roomId, hostMemberId)
        requireBusiness(room.status == RoomStatus.RECRUITING, CoreErrorType.ROOM_NOT_RECRUITING)

        val application = loadPendingApplication(roomId, applicationId)

        val now = LocalDateTime.now()
        val current = participationRepository.countByRoomIdAndDeletedAtIsNull(roomId).toInt()
        requireBusiness(current < room.maxCapacity, CoreErrorType.ROOM_CAPACITY_FULL)

        participationRepository.save(
            ParticipationEntity(
                roomId = roomId,
                memberId = application.applicantMemberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
        application.accept(hostMemberId, now)

        return ApplicationDecision(
            applicationId = application.id,
            status = application.status,
            currentParticipants = current + 1,
            maxCapacity = room.maxCapacity.toInt(),
        )
    }

    // 반려. 정원·참여자에 영향이 없어 룸 잠금은 필요 없다. 방장 권한과 대기 상태만 확인한다.
    @Transactional
    fun reject(roomId: UUID, applicationId: Long, hostMemberId: UUID, reason: String?): ApplicationDecision {
        val room = loadActiveRoomAsHost(roomId, hostMemberId)
        val application = loadPendingApplication(roomId, applicationId)

        application.reject(hostMemberId, reason, LocalDateTime.now())

        val current = participationRepository.countByRoomIdAndDeletedAtIsNull(roomId).toInt()
        return ApplicationDecision(
            applicationId = application.id,
            status = application.status,
            currentParticipants = current,
            maxCapacity = room.maxCapacity.toInt(),
        )
    }

    private fun loadPendingApplication(roomId: UUID, applicationId: Long): RoomApplicationEntity {
        val application = requireFound(
            roomApplicationRepository.findByIdAndRoomIdAndDeletedAtIsNull(applicationId, roomId),
            CoreErrorType.APPLICATION_NOT_FOUND,
        )
        requireBusiness(application.isPending(), CoreErrorType.APPLICATION_ALREADY_HANDLED)
        return application
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
}
