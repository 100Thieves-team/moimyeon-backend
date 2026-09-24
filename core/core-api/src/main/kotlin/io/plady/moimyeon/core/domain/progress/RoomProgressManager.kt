package io.plady.moimyeon.core.domain.progress

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.RoomLifecycleNotificationEvent
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

@Component
class RoomProgressManager(
    private val roomRepository: RoomRepository,
    private val participationFinder: ParticipationFinder,
    private val attendanceRepository: AttendanceRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun complete(command: RoomProgressCompletionCommand): RoomProgressCompletionResult {
        log.debug { "room-progress.manager.complete roomId=${command.roomId} completedByMemberId=${command.completedByMemberId}" }
        val room = requireFound(
            roomRepository.findByIdForUpdate(command.roomId)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireBusiness(room.canComplete(), CoreErrorType.ROOM_PROGRESS_NOT_COMPLETABLE)

        val confirmedParticipantIds = participationFinder.getConfirmedParticipantIds(command.roomId)
        room.complete()
        roomStatusLogRepository.save(
            RoomStatusLogEntity.byMember(
                roomId = command.roomId,
                transitionType = RoomStatus.COMPLETED,
                handlerMemberId = command.completedByMemberId,
                occurredAt = command.completedAt,
            ),
        )
        confirmedParticipantIds.forEach { memberId ->
            publish(EventType.ROOM_COMPLETED, command.roomId, memberId)
        }

        return RoomProgressCompletionResult(status = room.status)
    }

    @Transactional
    fun recordAttendances(command: RoomAttendanceRecordCommand): RoomAttendanceRecordResult {
        log.debug { "room-progress.manager.record-attendances roomId=${command.roomId} recorderMemberId=${command.recorderMemberId}" }
        val room = requireFound(
            roomRepository.findByIdForUpdate(command.roomId)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireBusiness(room.status == RoomStatus.COMPLETED, CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE)
        requireBusiness(
            attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(command.roomId).isEmpty(),
            CoreErrorType.ROOM_PROGRESS_ATTENDANCE_ALREADY_RECORDED,
        )

        val confirmedParticipantIds = participationFinder.getConfirmedParticipantIds(command.roomId)
        val attendanceMemberIds = command.attendances.map(Attendance::memberId)
        requireBusiness(
            attendanceMemberIds.size == confirmedParticipantIds.size &&
                attendanceMemberIds.toSet() == confirmedParticipantIds.toSet(),
            CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH,
        )

        attendanceRepository.saveAllAndFlush(
            command.attendances.map { attendance ->
                AttendanceEntity(
                    roomId = command.roomId,
                    memberId = attendance.memberId,
                    status = attendance.status,
                    recorderMemberId = command.recorderMemberId,
                    recordedAt = command.recordedAt,
                )
            },
        )
        val attended = command.attendances.filter { it.status == AttendanceStatus.ATTENDED }
        if (attended.size >= 2) {
            attended.forEach { attendance ->
                publish(EventType.ROOM_REVIEW_REQUESTED, command.roomId, attendance.memberId)
            }
        }

        return RoomAttendanceRecordResult(
            attendances = command.attendances.toList(),
        )
    }

    private fun publish(eventType: EventType, roomId: java.util.UUID, recipientMemberId: java.util.UUID) {
        applicationEventPublisher.publishEvent(
            RoomLifecycleNotificationEvent(
                eventType = eventType,
                roomId = roomId,
                recipientMemberId = recipientMemberId,
            ),
        )
    }
}
