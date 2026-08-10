package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RoomProgressManager(
    private val roomRepository: RoomRepository,
    private val participationFinder: ParticipationFinder,
    private val attendanceRepository: AttendanceRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) {
    @Transactional
    fun start(command: RoomProgressStartCommand): RoomProgressStartResult {
        val room = requireFound(
            roomRepository.findByIdForUpdate(command.roomId)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireBusiness(
            room.canStartProgress(),
            CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE,
        )
        val confirmedParticipantIds = participationFinder.getConfirmedParticipantIds(command.roomId)
        val attendanceMemberIds = command.attendances.map(Attendance::memberId)
        requireBusiness(
            attendanceMemberIds.size == confirmedParticipantIds.size &&
                attendanceMemberIds.toSet() == confirmedParticipantIds.toSet(),
            CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH,
        )
        val hostMemberId = participationFinder.getHostMemberId(command.roomId)

        room.startProgress()
        attendanceRepository.saveAllAndFlush(
            command.attendances.map { attendance ->
                AttendanceEntity(
                    roomId = command.roomId,
                    memberId = attendance.memberId,
                    status = attendance.status,
                    recorderMemberId = command.startedByMemberId,
                    recordedAt = command.startedAt,
                )
            },
        )
        roomStatusLogRepository.save(
            RoomStatusLogEntity(
                roomId = command.roomId,
                transitionType = RoomStatus.IN_PROGRESS,
                handlerMemberId = command.startedByMemberId,
                occurredAt = command.startedAt,
            ),
        )

        return RoomProgressStartResult(
            status = room.status,
            hostMemberId = hostMemberId,
            attendances = command.attendances.toList(),
        )
    }
}
