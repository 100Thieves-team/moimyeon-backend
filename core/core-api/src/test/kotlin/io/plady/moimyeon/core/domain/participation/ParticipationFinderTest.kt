package io.plady.moimyeon.core.domain.participation

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ParticipationFinderTest {
    private val participationRepository = mockk<ParticipationRepository>()
    private val participationFinder = ParticipationFinder(participationRepository)

    @Test
    fun `현재 참여 여부는 삭제되지 않은 JOINED 참여로 판정한다`() {
        val roomId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        every {
            participationRepository.existsByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationStatus.JOINED,
            )
        } returns true

        assertThat(participationFinder.isParticipating(roomId, memberId)).isTrue()
    }

    @Test
    fun `확정 참여 여부는 룸 확정 시각의 참여 상태로 판정한다`() {
        val roomId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        every {
            participationRepository.existsAtRoomConfirmation(roomId, memberId)
        } returns true

        assertThat(participationFinder.wasConfirmedParticipant(roomId, memberId)).isTrue()
    }

    @Test
    fun `확정 참여자 목록은 저장소가 반환한 확정 당시 순서를 유지한다`() {
        val roomId = UUID.randomUUID()
        val firstMemberId = UUID.randomUUID()
        val secondMemberId = UUID.randomUUID()
        every {
            participationRepository.findAllAtRoomConfirmation(roomId)
        } returns listOf(
            mockk { every { memberId } returns firstMemberId },
            mockk { every { memberId } returns secondMemberId },
        )

        assertThat(participationFinder.getConfirmedParticipantIds(roomId))
            .containsExactly(firstMemberId, secondMemberId)
    }
}
