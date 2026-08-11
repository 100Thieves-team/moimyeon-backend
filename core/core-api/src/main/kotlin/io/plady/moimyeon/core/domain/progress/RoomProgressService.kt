package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class RoomProgressService(
    private val accessValidator: RoomProgressAccessValidator,
    private val participationFinder: ParticipationFinder,
    private val progressManager: RoomProgressManager,
    private val progressReader: RoomProgressReader,
    private val clock: Clock,
) {
    fun start(
        startedByMemberId: UUID,
        roomId: UUID,
        attendances: List<Attendance>,
    ): RoomProgressStartResult {
        accessValidator.validateStarter(roomId, startedByMemberId)
        return progressManager.start(
            RoomProgressStartCommand(
                roomId = roomId,
                startedByMemberId = startedByMemberId,
                attendances = attendances.toList(),
                startedAt = now(),
            ),
        )
    }

    fun getMyAttendance(memberId: UUID, roomId: UUID): Attendance {
        accessValidator.validateAttendanceViewer(roomId, memberId)
        return progressReader.getAttendance(roomId, memberId)
    }

    fun getRail(memberId: UUID, roomId: UUID): ProgressRail {
        accessValidator.validateRailViewer(roomId, memberId)
        val confirmedParticipantIds = participationFinder.getConfirmedParticipantIds(roomId)
        return ProgressRail.from(confirmedParticipantIds)
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
