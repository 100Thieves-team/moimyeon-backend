package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.RoomStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class RoomProgressServiceTest {
    private val accessValidator = mockk<RoomProgressAccessValidator>()
    private val participationFinder = mockk<ParticipationFinder>()
    private val manager = mockk<RoomProgressManager>()
    private val reader = mockk<RoomProgressReader>()
    private val clock = Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC)
    private val service = RoomProgressService(accessValidator, participationFinder, manager, reader, clock)
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 8, 10, 3, 0)

    @Test
    fun `완료는 출석과 함께 한 명령으로 전달한다`() {
        val attendances = listOf(
            Attendance(hostId, AttendanceStatus.ATTENDED),
            Attendance(participantId, AttendanceStatus.ABSENT),
        )
        val command = RoomProgressCompletionCommand(roomId, hostId, attendances, now)
        justRun { accessValidator.validateCompleter(roomId, hostId) }
        every { manager.complete(command) } returns RoomProgressCompletionResult(RoomStatus.COMPLETED, attendances)

        val result = service.complete(hostId, roomId, attendances)

        assertThat(result.status).isEqualTo(RoomStatus.COMPLETED)
        assertThat(result.attendances).containsExactlyElementsOf(attendances)
        verifyOrder {
            accessValidator.validateCompleter(roomId, hostId)
            manager.complete(command)
        }
    }

    @Test
    fun `진행 레일은 현재 확정 참여자 순서로 구성한다`() {
        val confirmedParticipantIds = listOf(hostId, participantId)
        justRun { accessValidator.validateInProgressParticipant(roomId, hostId) }
        every { participationFinder.getConfirmedParticipantIds(roomId) } returns confirmedParticipantIds

        val result = service.getRail(hostId, roomId)

        assertThat(result.blocks.first()).isEqualTo(ProgressBlock.Opening)
        assertThat(result.blocks.last()).isEqualTo(ProgressBlock.Closing)
        assertThat(result.blocks.filterIsInstance<ProgressBlock.Round>().map { it.targetMemberId })
            .containsExactlyElementsOf(confirmedParticipantIds)
        verifyOrder {
            accessValidator.validateInProgressParticipant(roomId, hostId)
            participationFinder.getConfirmedParticipantIds(roomId)
        }
    }
}
