package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class RoomProgressServiceTest {
    private val accessValidator = mockk<RoomProgressAccessValidator>()
    private val participationFinder = mockk<ParticipationFinder>()
    private val progressManager = mockk<RoomProgressManager>()
    private val progressReader = mockk<RoomProgressReader>()
    private val clock = Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC)
    private val service = RoomProgressService(
        accessValidator,
        participationFinder,
        progressManager,
        progressReader,
        clock,
    )

    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val participant1Id = UUID.randomUUID()
    private val participant2Id = UUID.randomUUID()
    private val participant3Id = UUID.randomUUID()
    private val outsiderMemberId = UUID.randomUUID()
    private val confirmedParticipantIds = listOf(
        hostMemberId,
        participant1Id,
        participant2Id,
        participant3Id,
    )
    private val currentTime = LocalDateTime.of(2026, 8, 10, 3, 0)

    @Test
    fun `방장이 확정 룸을 시작하면 확정 참여자 전원의 출석을 한 번에 기록한다`() {
        val selectedAttendances = attendanceSelection(setOf(hostMemberId, participant1Id, participant2Id))
        val command = startCommand(selectedAttendances)
        val started = startedProgress(command.attendances)
        givenRoomCanStart()
        every { progressManager.start(command) } returns started

        val result = service.start(hostMemberId, roomId, selectedAttendances)

        assertThat(result.status).isEqualTo(RoomStatus.IN_PROGRESS)
        assertThat(result.attendances).containsExactlyInAnyOrderElementsOf(command.attendances)
        assertThat(result.attendances).hasSize(confirmedParticipantIds.size)
        verifyOrder {
            accessValidator.validateStarter(roomId, hostMemberId)
            progressManager.start(command)
        }
    }

    @Test
    fun `참여 패널에서 확정 참여자가 방장을 불참으로 선택해 제출하면 그대로 기록하고 기존 방장은 유지한다`() {
        val selectedAttendances = attendanceSelection(setOf(participant1Id, participant2Id, participant3Id))
        val command = startCommand(selectedAttendances, startedByMemberId = participant1Id)
        givenRoomCanStart(participant1Id)
        every {
            progressManager.start(command)
        } returns startedProgress(command.attendances)

        val result = service.start(participant1Id, roomId, selectedAttendances)

        assertThat(result.hostMemberId).isEqualTo(hostMemberId)
        assertThat(command.startedByMemberId).isEqualTo(participant1Id)
        assertThat(result.attendances).containsExactlyInAnyOrderElementsOf(selectedAttendances)
        assertThat(result.attendances.single { it.memberId == hostMemberId }.status)
            .isEqualTo(AttendanceStatus.ABSENT)
        verifyOrder {
            accessValidator.validateStarter(roomId, participant1Id)
            progressManager.start(command)
        }
    }

    @Test
    fun `확정 참여자가 아니면 참여 패널의 출석을 제출할 수 없다`() {
        every {
            accessValidator.validateStarter(roomId, outsiderMemberId)
        } throws CoreException(CoreErrorType.ROOM_PROGRESS_START_FORBIDDEN)

        assertProgressFails(CoreErrorType.ROOM_PROGRESS_START_FORBIDDEN) {
            service.start(
                outsiderMemberId,
                roomId,
                listOf(Attendance(outsiderMemberId, AttendanceStatus.ATTENDED)),
            )
        }

        verify(exactly = 0) { participationFinder.getConfirmedParticipantIds(any()) }
        verify(exactly = 0) { progressManager.start(any()) }
    }

    @Test
    fun `확정 전 룸은 확정 참여자를 읽거나 진행 기록을 만들지 않는다`() {
        every {
            accessValidator.validateStarter(roomId, hostMemberId)
        } throws CoreException(CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE)

        assertProgressFails(CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE) {
            service.start(
                hostMemberId,
                roomId,
                listOf(Attendance(hostMemberId, AttendanceStatus.ATTENDED)),
            )
        }

        verify(exactly = 0) { participationFinder.getConfirmedParticipantIds(any()) }
        verify(exactly = 0) { progressManager.start(any()) }
    }

    @Test
    fun `진행 중인 룸에 다시 시작을 요청하면 최초 결과를 반환하지 않고 거부한다`() {
        every {
            accessValidator.validateStarter(roomId, hostMemberId)
        } throws CoreException(CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE)

        assertProgressFails(CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE) {
            service.start(
                hostMemberId,
                roomId,
                attendanceSelection(confirmedParticipantIds.toSet()),
            )
        }

        verify(exactly = 0) { progressManager.start(any()) }
    }

    @Test
    fun `출석 상태는 출석과 불참 두 종류뿐이다`() {
        assertThat(AttendanceStatus.entries)
            .containsExactlyInAnyOrder(AttendanceStatus.ATTENDED, AttendanceStatus.ABSENT)
    }

    @Test
    fun `참여자는 자신의 출석 결과만 조회한다`() {
        val myAttendance = Attendance(participant3Id, AttendanceStatus.ABSENT)
        justRun { accessValidator.validateAttendanceViewer(roomId, participant3Id) }
        every { progressReader.getAttendance(roomId, participant3Id) } returns myAttendance

        val result = service.getMyAttendance(participant3Id, roomId)

        assertThat(result).isEqualTo(myAttendance)
        verifyOrder {
            accessValidator.validateAttendanceViewer(roomId, participant3Id)
            progressReader.getAttendance(roomId, participant3Id)
        }
    }

    @Test
    fun `시작 후 도착한 참여자는 최초에 정해진 불참 결과를 조회한다`() {
        val absent = Attendance(participant3Id, AttendanceStatus.ABSENT)
        justRun { accessValidator.validateAttendanceViewer(roomId, participant3Id) }
        every { progressReader.getAttendance(roomId, participant3Id) } returns absent

        val result = service.getMyAttendance(participant3Id, roomId)

        assertThat(result.status).isEqualTo(AttendanceStatus.ABSENT)
    }

    @Test
    fun `확정 참여자가 아니면 출석 결과나 진행 레일 블록을 조회하지 않는다`() {
        every {
            accessValidator.validateAttendanceViewer(roomId, outsiderMemberId)
        } throws CoreException(CoreErrorType.ROOM_PROGRESS_FORBIDDEN)
        every {
            accessValidator.validateRailViewer(roomId, outsiderMemberId)
        } throws CoreException(CoreErrorType.ROOM_PROGRESS_FORBIDDEN)

        assertProgressFails(CoreErrorType.ROOM_PROGRESS_FORBIDDEN) {
            service.getMyAttendance(outsiderMemberId, roomId)
        }
        assertProgressFails(CoreErrorType.ROOM_PROGRESS_FORBIDDEN) {
            service.getRail(outsiderMemberId, roomId)
        }

        verify(exactly = 0) { progressReader.getAttendance(any(), any()) }
        verify(exactly = 0) { participationFinder.getConfirmedParticipantIds(any()) }
    }

    @Test
    fun `참여자는 예상 시각과 소요 시간 없이 오프닝 라운드 클로징 블록 목록을 조회한다`() {
        justRun { accessValidator.validateRailViewer(roomId, participant1Id) }
        every { participationFinder.getConfirmedParticipantIds(roomId) } returns confirmedParticipantIds

        val result = service.getRail(participant1Id, roomId)

        assertThat(result.blocks.first()).isEqualTo(ProgressBlock.Opening)
        assertThat(result.blocks.last()).isEqualTo(ProgressBlock.Closing)
        assertThat(result.blocks.filterIsInstance<ProgressBlock.Round>().map { it.targetMemberId })
            .containsExactlyElementsOf(confirmedParticipantIds)
        verifyOrder {
            accessValidator.validateRailViewer(roomId, participant1Id)
            participationFinder.getConfirmedParticipantIds(roomId)
        }
    }

    private fun givenRoomCanStart(starterMemberId: UUID = hostMemberId) {
        justRun { accessValidator.validateStarter(roomId, starterMemberId) }
    }

    private fun attendanceSelection(attendedMemberIds: Set<UUID>): List<Attendance> {
        return confirmedParticipantIds.map { memberId ->
            Attendance(
                memberId = memberId,
                status = if (memberId in attendedMemberIds) {
                    AttendanceStatus.ATTENDED
                } else {
                    AttendanceStatus.ABSENT
                },
            )
        }
    }

    private fun startCommand(
        attendances: List<Attendance>,
        startedByMemberId: UUID = hostMemberId,
    ): RoomProgressStartCommand {
        return RoomProgressStartCommand(
            roomId = roomId,
            startedByMemberId = startedByMemberId,
            attendances = attendances,
            startedAt = currentTime,
        )
    }

    private fun startedProgress(
        attendances: List<Attendance>,
        hostMemberId: UUID = this.hostMemberId,
    ): RoomProgressStartResult {
        return RoomProgressStartResult(
            status = RoomStatus.IN_PROGRESS,
            hostMemberId = hostMemberId,
            attendances = attendances,
        )
    }

    private fun assertProgressFails(errorType: CoreErrorType, block: () -> Unit) {
        assertThatThrownBy(block)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
