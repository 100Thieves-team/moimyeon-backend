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
    private val roomParticipationReader = mockk<RoomParticipationReader>()
    private val roomLeaveManager = mockk<RoomLeaveManager>()
    private val service = RoomParticipantService(
        participationValidator,
        roomParticipantReader,
        roomParticipationReader,
        roomLeaveManager,
    )

    @Test
    fun `회원이 현재 참여 중이거나 완료한 룸을 참여 이력으로 조회한다`() {
        val memberId = UUID.randomUUID()
        val history = RoomParticipationHistory(emptyList(), emptyList())
        every { roomParticipationReader.getHistory(memberId) } returns history

        val result = service.getParticipationHistory(memberId)

        assertThat(result).isSameAs(history)
        verify(exactly = 1) { roomParticipationReader.getHistory(memberId) }
    }
}
