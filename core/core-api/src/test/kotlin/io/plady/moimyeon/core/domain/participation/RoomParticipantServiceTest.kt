package io.plady.moimyeon.core.domain.participation

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.room.RoomLeaveManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
}
