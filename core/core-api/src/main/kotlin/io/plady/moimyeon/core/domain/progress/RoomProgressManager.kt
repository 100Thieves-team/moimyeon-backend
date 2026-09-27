package io.plady.moimyeon.core.domain.progress

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.event.OutboxEventPublisher
import io.plady.moimyeon.core.event.payload.RoomCompletedEventPayload
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class RoomProgressManager(
    private val roomRepository: RoomRepository,
    private val participationFinder: ParticipationFinder,
    private val participationValidator: ParticipationValidator,
    private val attendanceRepository: AttendanceRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val outboxEventPublisher: OutboxEventPublisher,
) {
    @Transactional
    fun complete(command: RoomProgressCompletionCommand): RoomProgressCompletionResult {
        log.debug { "room-progress.manager.complete roomId=${command.roomId} completedByMemberId=${command.completedByMemberId}" }
        val room = requireFound(
            roomRepository.findByIdForUpdate(command.roomId)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        participationValidator.validateHost(command.roomId, command.completedByMemberId)
        if (room.status == RoomStatus.COMPLETED) return replayCompletion(room, command)
        requireBusiness(room.canComplete(), CoreErrorType.ROOM_PROGRESS_NOT_COMPLETABLE)

        val confirmedParticipantIds = participationFinder.getConfirmedParticipantIds(command.roomId)
        val attendanceMemberIds = command.attendances.map(Attendance::memberId)
        requireBusiness(
            attendanceMemberIds.size == confirmedParticipantIds.size &&
                attendanceMemberIds.toSet() == confirmedParticipantIds.toSet(),
            CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH,
        )

        completeRoom(
            room,
            RoomStatusLogEntity.byMember(
                roomId = command.roomId,
                transitionType = RoomStatus.COMPLETED,
                handlerMemberId = command.completedByMemberId,
                occurredAt = command.completedAt,
            ),
            completedByMemberId = command.completedByMemberId,
            confirmedParticipantIds = confirmedParticipantIds,
            attendances = command.attendances,
            recorderMemberId = command.completedByMemberId,
        )
        return RoomProgressCompletionResult(status = room.status, attendances = command.attendances)
    }

    fun findOverdueRoomIds(now: LocalDateTime): List<UUID> = roomRepository
        .findConfirmedStartedBefore(now.minusHours(RoomEntity.AUTO_COMPLETION_HOURS), PageRequest.of(0, OVERDUE_BATCH_SIZE))
        .map { it.id }

    // 후보 조회 뒤 수동 완료·방장 이탈이 끼어들 수 있어 잠근 뒤 다시 판정한다.
    // 입력된 출석이 없으므로 확정 참여자 전원을 출석으로, 기록자는 현재 방장으로 남긴다.
    @Transactional
    fun completeOverdue(roomId: UUID, now: LocalDateTime): Boolean {
        log.debug { "room-progress.manager.completeOverdue roomId=$roomId" }
        val room = roomRepository.findByIdForUpdate(roomId)?.takeIf { it.isActive() } ?: return false
        if (!room.isAutoCompletable(now)) return false

        val confirmedParticipantIds = participationFinder.getConfirmedParticipantIds(roomId)
        completeRoom(
            room,
            RoomStatusLogEntity.bySystem(roomId = roomId, transitionType = RoomStatus.COMPLETED, occurredAt = now),
            completedByMemberId = null,
            confirmedParticipantIds = confirmedParticipantIds,
            attendances = confirmedParticipantIds.map { Attendance(it, AttendanceStatus.ATTENDED) },
            recorderMemberId = participationFinder.getHostMemberId(roomId),
        )
        return true
    }

    // 응답을 못 받고 같은 출석으로 다시 보낸 요청이면 이전 성공 결과를 돌려준다. 내용이 다르면 이미 완료된 룸이다.
    private fun replayCompletion(room: RoomEntity, command: RoomProgressCompletionCommand): RoomProgressCompletionResult {
        val recorded = attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(room.id)
            .map { Attendance(it.memberId, it.status) }
        requireBusiness(
            recorded.size == command.attendances.size && recorded.toSet() == command.attendances.toSet(),
            CoreErrorType.ROOM_PROGRESS_NOT_COMPLETABLE,
        )
        return RoomProgressCompletionResult(status = room.status, attendances = command.attendances)
    }

    private fun completeRoom(
        room: RoomEntity,
        statusLog: RoomStatusLogEntity,
        completedByMemberId: UUID?,
        confirmedParticipantIds: List<UUID>,
        attendances: List<Attendance>,
        recorderMemberId: UUID,
    ) {
        room.complete()
        roomStatusLogRepository.save(statusLog)
        attendanceRepository.saveAllAndFlush(
            attendances.map { attendance ->
                AttendanceEntity(
                    roomId = room.id,
                    memberId = attendance.memberId,
                    status = attendance.status,
                    recorderMemberId = recorderMemberId,
                    recordedAt = statusLog.occurredAt,
                )
            },
        )
        outboxEventPublisher.publish(
            EventType.ROOM_COMPLETED,
            RoomCompletedEventPayload(
                roomId = room.id,
                roomTitle = room.title,
                completedByMemberId = completedByMemberId,
                confirmedParticipantMemberIds = confirmedParticipantIds,
                attendedMemberIds = attendances.filter { it.status == AttendanceStatus.ATTENDED }.map { it.memberId },
            ),
        )
    }

    private companion object {
        const val OVERDUE_BATCH_SIZE = 100
    }
}
