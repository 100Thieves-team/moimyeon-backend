package io.plady.moimyeon.core.domain.participation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParticipationSlotsTest {
    @Test
    fun `점유 수만큼 남은 슬롯이 줄어든다`() {
        assertThat(ParticipationSlots.of(0)).isEqualTo(ParticipationSlots(occupied = 0, limit = 3, remaining = 3))
        assertThat(ParticipationSlots.of(2)).isEqualTo(ParticipationSlots(occupied = 2, limit = 3, remaining = 1))
    }

    // 4는 지금 만들 수 없는 상태지만(게이트가 3에서 막는다) 한도가 회원별로 줄어드는 날 생긴다.
    // 화면이 min 계산에 쓰는 값이라 음수가 나가면 안 된다.
    @Test
    fun `점유가 한도 이상이면 남은 슬롯은 0이다`() {
        assertThat(ParticipationSlots.of(3).remaining).isZero()
        assertThat(ParticipationSlots.of(4).remaining).isZero()
    }
}
