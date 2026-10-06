package io.plady.moimyeon.core.domain.participation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.room.RoomLeaveManager
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomParticipantServiceTest {
    private val participationValidator = mockk<ParticipationValidator>()
    private val roomParticipantReader = mockk<RoomParticipantReader>()
    private val participationFinder = mockk<ParticipationFinder>()
    private val roomLeaveManager = mockk<RoomLeaveManager>()
    private val service = RoomParticipantService(
        participationValidator,
        roomParticipantReader,
        participationFinder,
        roomLeaveManager,
    )

    @Test
    fun `회원이 참여 중인 룸 식별자를 조회한다`() {
        val memberId = UUID.randomUUID()
        val roomIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        every { participationFinder.getParticipatingRoomIds(memberId) } returns roomIds

        val result = service.getParticipatingRoomIds(memberId)

        assertThat(result).containsExactlyElementsOf(roomIds)
        verify(exactly = 1) { participationFinder.getParticipatingRoomIds(memberId) }
    }

    @Test
    fun `명부 조회는 참여자 게이트를 통과한 뒤 현재 명단과 확정 명단을 함께 돌려준다`() {
        val viewerId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val leftMemberId = UUID.randomUUID()
        val current = listOf(
            RoomParticipant(
                memberId = viewerId,
                nickname = "든든한곰",
                isHost = true,
                joinedAt = LocalDateTime.of(2026, 8, 1, 10, 0),
                resumeSummary = null,
                resumeSubmissionId = null,
                canViewOriginal = false,
            ),
        )
        val confirmed = listOf(ConfirmedParticipant(viewerId, "든든한곰"), ConfirmedParticipant(leftMemberId, "라이언"))
        every { participationValidator.validateParticipant(roomId, viewerId) } returns Unit
        val roster = RoomParticipants(participants = current, confirmedParticipants = confirmed)
        every { roomParticipantReader.getRoster(roomId, viewerId) } returns roster

        val result = service.getParticipants(viewerId, roomId)

        assertThat(result).isEqualTo(roster)
        verify(exactly = 1) { participationValidator.validateParticipant(roomId, viewerId) }
    }

    @Test
    fun `참여자가 아니면 명단을 읽지 않고 E1419 로 거부한다`() {
        val viewerId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        every { participationValidator.validateParticipant(roomId, viewerId) } throws
            CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        assertThatThrownBy { service.getParticipants(viewerId, roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)
            }
        verify(exactly = 0) { roomParticipantReader.getRoster(any(), any()) }
    }
}
