package io.plady.moimyeon.core.domain.room

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecruitStatusTest {
    private val capacity = RoomCapacity(min = 2, max = 4)

    @Test
    fun `현재 인원이 최대 인원 이상이면 모집 마감이다`() {
        assertThat(RecruitStatus.of(4, capacity)).isEqualTo(RecruitStatus.CLOSED)
    }

    @Test
    fun `현재 인원이 최대 인원 미만이면 모집 중이다`() {
        assertThat(RecruitStatus.of(3, capacity)).isEqualTo(RecruitStatus.RECRUITING)
    }
}
