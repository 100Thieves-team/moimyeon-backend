package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifyOrder
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
    private val manager = mockk<RoomProgressManager>()
    private val reader = mockk<RoomProgressReader>()
    private val clock = Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC)
    private val service = RoomProgressService(accessValidator, manager, reader, clock)
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()
    private val participantId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 8, 10, 3, 0)

    @Test
    fun `완료는 출석 없이 별도 명령으로 전달한다`() {
        val command = RoomProgressCompletionCommand(roomId, hostId, now)
        justRun { accessValidator.validateCompleter(roomId, hostId) }
        every { manager.complete(command) } returns RoomProgressCompletionResult(RoomStatus.COMPLETED)

        assertThat(service.complete(hostId, roomId).status).isEqualTo(RoomStatus.COMPLETED)

        verifyOrder {
            accessValidator.validateCompleter(roomId, hostId)
            manager.complete(command)
        }
    }

    @Test
    fun `출석은 완료 이후 별도 명령으로 전달한다`() {
        val attendances = listOf(
            Attendance(hostId, AttendanceStatus.ATTENDED),
            Attendance(participantId, AttendanceStatus.ABSENT),
        )
        val command = RoomAttendanceRecordCommand(roomId, hostId, attendances, now)
        justRun { accessValidator.validateAttendanceRecorder(roomId, hostId) }
        every { manager.recordAttendances(command) } returns RoomAttendanceRecordResult(attendances)

        assertThat(service.recordAttendances(hostId, roomId, attendances).attendances).containsExactlyElementsOf(attendances)

        verifyOrder {
            accessValidator.validateAttendanceRecorder(roomId, hostId)
            manager.recordAttendances(command)
        }
    }
}
