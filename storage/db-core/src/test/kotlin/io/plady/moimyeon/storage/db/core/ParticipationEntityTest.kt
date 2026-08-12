package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ParticipationEntityTest {
    private val now = LocalDateTime.of(2026, 8, 12, 12, 0)
    private val memberId = UUID.randomUUID()

    // leftAt 하나만 빠져도 확정 후 이탈 판정(left_at > confirmed_at)이 조용히 틀어진다.
    // 셋을 한 메서드가 함께 쓰는지가 이 테스트의 전부다.
    @Test
    fun `나가면 상태와 나간 시각과 처리자가 함께 기록된다`() {
        val participation = joined()

        participation.leave(now, memberId)

        assertThat(participation.status).isEqualTo(ParticipationStatus.LEFT)
        assertThat(participation.leftAt).isEqualTo(now)
        assertThat(participation.leftByMemberId).isEqualTo(memberId)
    }

    // leftByMemberId 가 자진 이탈과 강퇴를 가른다(재신청 차단 판정).
    @Test
    fun `내보내면 처리자가 나가는 사람과 다르게 남는다`() {
        val participation = joined()
        val hostMemberId = UUID.randomUUID()

        participation.leave(now, hostMemberId)

        assertThat(participation.leftByMemberId).isEqualTo(hostMemberId)
    }

    @Test
    fun `참여자를 방장으로 올린다`() {
        val participation = joined()

        participation.promoteToHost()

        assertThat(participation.participationRole).isEqualTo(ParticipationRole.HOST)
    }

    private fun joined(): ParticipationEntity = ParticipationEntity(
        roomId = UUID.randomUUID(),
        memberId = memberId,
        participationRole = ParticipationRole.PARTICIPANT,
        status = ParticipationStatus.JOINED,
        joinedAt = now.minusDays(1),
    )
}
