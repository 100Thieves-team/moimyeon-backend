package io.plady.moimyeon.core.domain.roomcomment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RoomCommentDuplicateTest {
    private val lastCreatedAt = LocalDateTime.of(2026, 8, 14, 10, 0, 0)

    @Test
    fun `같은 내용을 10초 이내에 다시 보내면 중복이다`() {
        assertThat(RoomCommentDuplicate.isDuplicate("반가워요", lastCreatedAt, "반가워요", lastCreatedAt.plusSeconds(3))).isTrue()
        assertThat(RoomCommentDuplicate.isDuplicate("반가워요", lastCreatedAt, "반가워요", lastCreatedAt.plusSeconds(10))).isTrue()
    }

    @Test
    fun `10초가 지나면 같은 내용이어도 새 글이다`() {
        assertThat(RoomCommentDuplicate.isDuplicate("반가워요", lastCreatedAt, "반가워요", lastCreatedAt.plusSeconds(11))).isFalse()
    }

    @Test
    fun `내용이 다르면 시간과 무관하게 새 글이다`() {
        assertThat(RoomCommentDuplicate.isDuplicate("반가워요", lastCreatedAt, "안녕하세요", lastCreatedAt.plusSeconds(1))).isFalse()
    }
}
