package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomEntityTest {
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Test
    fun `확정 룸은 예정 시각과 무관하게 명시적으로 완료할 수 있다`() {
        val room = recruitingRoom().apply { confirm() }

        assertThat(room.canComplete()).isTrue()
        room.complete()

        assertThat(room.status).isEqualTo(RoomStatus.COMPLETED)
    }

    @Test
    fun `확정 룸은 예정 시각 8시간부터 자동 완료할 수 있다`() {
        val room = recruitingRoom().apply { confirm() }
        val deadline = room.startAt.plusHours(8)

        assertThat(room.isAutoCompletable(deadline.minusNanos(1))).isFalse()
        assertThat(room.isAutoCompletable(deadline)).isTrue()
    }

    @Test
    fun `확정 룸은 다시 모집 중으로 전환할 수 있다`() {
        val room = recruitingRoom().apply { confirm() }

        room.reopenRecruiting()

        assertThat(room.status).isEqualTo(RoomStatus.RECRUITING)
    }

    @Test
    fun `모집 중 완료와 모집 재개는 실패한다`() {
        val room = recruitingRoom()

        assertThatThrownBy(room::complete).isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy(room::reopenRecruiting).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `모집 중과 확정 룸은 나갈 수 있고 종료된 룸은 나갈 수 없다`() {
        val recruiting = recruitingRoom()
        val confirmed = recruitingRoom().apply { confirm() }
        val completed = recruitingRoom().apply {
            confirm()
            complete()
        }
        val canceled = recruitingRoom().apply { cancel() }

        assertThat(recruiting.canLeave()).isTrue()
        assertThat(confirmed.canLeave()).isTrue()
        assertThat(completed.canLeave()).isFalse()
        assertThat(canceled.canLeave()).isFalse()
    }

    private fun recruitingRoom(): RoomEntity = RoomEntity(
        id = UUID.randomUUID(),
        jobPostingId = 1L,
        jobRoleId = 1L,
        resumePublic = false,
        sigunguId = null,
        title = "백엔드 모의면접 함께 준비해요",
        description = null,
        interviewStage = InterviewStage.FIRST,
        interviewType = InterviewType.JOB,
        meetingType = MeetingType.ONLINE,
        minCapacity = 2,
        maxCapacity = 4,
        startAt = now.plusDays(3),
        durationMinutes = 60,
    )
}
