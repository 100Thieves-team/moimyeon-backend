package io.plady.moimyeon.core.domain.roomcomment

import io.plady.moimyeon.core.enums.RoomStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RoomCommentWindowTest {
    private val transitionAt = LocalDateTime.of(2026, 8, 14, 10, 0, 0)

    @Test
    fun `모집 중인 룸은 작성 가능하고 전환 예정이 없다`() {
        val window = RoomCommentWindow.of(RoomStatus.RECRUITING, null, transitionAt)

        assertThat(window.writable).isTrue()
        assertThat(window.readOnlyAt).isNull()
    }

    @Test
    fun `취소 후 24시간 안은 작성 가능하고 전환 예정 시각을 안내한다`() {
        val window = RoomCommentWindow.of(RoomStatus.CANCELED, transitionAt, transitionAt.plusHours(23))

        assertThat(window.writable).isTrue()
        assertThat(window.readOnlyAt).isEqualTo(transitionAt.plusHours(24))
    }

    @Test
    fun `취소 후 24시간이 지나면 읽기 전용이다`() {
        val window = RoomCommentWindow.of(RoomStatus.CANCELED, transitionAt, transitionAt.plusHours(25))

        assertThat(window.writable).isFalse()
        assertThat(window.readOnlyAt).isEqualTo(transitionAt.plusHours(24))
    }

    @Test
    fun `정확히 24시간이 되는 순간부터 읽기 전용이다`() {
        val window = RoomCommentWindow.of(RoomStatus.CANCELED, transitionAt, transitionAt.plusHours(24))

        assertThat(window.writable).isFalse()
    }

    @Test
    fun `완료 룸도 취소와 같은 식으로 판정한다`() {
        val open = RoomCommentWindow.of(RoomStatus.COMPLETED, transitionAt, transitionAt.plusHours(1))
        val closed = RoomCommentWindow.of(RoomStatus.COMPLETED, transitionAt, transitionAt.plusHours(24))

        assertThat(open.writable).isTrue()
        assertThat(closed.writable).isFalse()
    }

    @Test
    fun `터미널 상태인데 전이 로그가 없으면 읽기 전용으로 닫는다`() {
        val window = RoomCommentWindow.of(RoomStatus.CANCELED, null, transitionAt)

        assertThat(window.writable).isFalse()
        assertThat(window.readOnlyAt).isNull()
    }
}
