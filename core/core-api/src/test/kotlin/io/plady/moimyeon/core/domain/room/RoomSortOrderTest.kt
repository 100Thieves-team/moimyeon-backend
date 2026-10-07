package io.plady.moimyeon.core.domain.room

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoomSortOrderTest {
    @Test
    fun `지원하지 않는 정렬 값은 기본 정렬로 처리한다`() {
        assertThat(RoomSortOrder.from("POPULAR")).isEqualTo(RoomSortOrder.SCHEDULE)
    }

    @Test
    fun `정렬 값이 없으면 기본 정렬로 처리한다`() {
        assertThat(RoomSortOrder.from(null)).isEqualTo(RoomSortOrder.SCHEDULE)
    }
}
