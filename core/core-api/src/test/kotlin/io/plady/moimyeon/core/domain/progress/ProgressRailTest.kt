package io.plady.moimyeon.core.domain.progress

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ProgressRailTest {
    @Test
    fun `진행 레일은 오프닝 확정 참여자별 라운드 클로징 순서로 구성한다`() {
        val firstParticipantId = UUID.randomUUID()
        val secondParticipantId = UUID.randomUUID()

        val rail = ProgressRail.from(listOf(firstParticipantId, secondParticipantId))

        assertThat(rail.blocks).containsExactly(
            ProgressBlock.Opening,
            ProgressBlock.Round(firstParticipantId),
            ProgressBlock.Round(secondParticipantId),
            ProgressBlock.Closing,
        )
    }
}
