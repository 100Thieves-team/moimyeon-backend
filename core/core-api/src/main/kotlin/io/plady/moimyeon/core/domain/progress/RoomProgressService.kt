package io.plady.moimyeon.core.domain.progress

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class RoomProgressService(
    private val accessValidator: RoomProgressAccessValidator,
    private val participationFinder: ParticipationFinder,
    private val progressManager: RoomProgressManager,
    private val progressReader: RoomProgressReader,
    private val clock: Clock,
) {
    fun complete(
        completedByMemberId: UUID,
        roomId: UUID,
    ): RoomProgressCompletionResult {
        log.debug { "room.progress.complete memberId=$completedByMemberId roomId=$roomId" }
        val completedAt = now()
        accessValidator.validateCompleter(roomId, completedByMemberId)
        return progressManager.complete(
            RoomProgressCompletionCommand(
                roomId = roomId,
                completedByMemberId = completedByMemberId,
                completedAt = completedAt,
            ),
        )
    }

    fun recordAttendances(
        recorderMemberId: UUID,
        roomId: UUID,
        attendances: List<Attendance>,
    ): RoomAttendanceRecordResult {
        log.debug { "room.attendance.record memberId=$recorderMemberId roomId=$roomId attendances=${attendances.size}" }
        accessValidator.validateAttendanceRecorder(roomId, recorderMemberId)
        return progressManager.recordAttendances(
            RoomAttendanceRecordCommand(
                roomId = roomId,
                recorderMemberId = recorderMemberId,
                attendances = attendances.toList(),
                recordedAt = now(),
            ),
        )
    }

    fun getMyAttendance(memberId: UUID, roomId: UUID): Attendance {
        accessValidator.validateAttendanceViewer(roomId, memberId)
        return progressReader.getAttendance(roomId, memberId)
    }

    fun getRail(memberId: UUID, roomId: UUID): ProgressRail {
        accessValidator.validateInProgressParticipant(roomId, memberId)
        return ProgressRail.from(participationFinder.getConfirmedParticipantIds(roomId))
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
