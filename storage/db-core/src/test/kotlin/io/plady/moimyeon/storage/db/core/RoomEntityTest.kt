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

// 룸이 가진 상태와 일정만으로 끝나는 전이 규칙은 메모리에서 검증한다.
// 참여 인원처럼 외부 데이터가 필요한 판정(확정·전원 제출)은 해당 흐름의 테스트가 본다.
class RoomEntityTest {
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

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

    @Test
    fun `생성된 룸은 모집 중이라 취소할 수 있다`() {
        val room = recruitingRoom()

        assertThat(room.status).isEqualTo(RoomStatus.RECRUITING)
        assertThat(room.canCancel()).isTrue()
    }

    @Test
    fun `취소하면 CANCELED 가 된다`() {
        val room = recruitingRoom()

        room.cancel()

        assertThat(room.status).isEqualTo(RoomStatus.CANCELED)
    }

    // 취소는 되돌릴 수 없다. 같은 요청이 두 번 와도 두 번째는 통과하지 않는다.
    @Test
    fun `취소된 룸은 다시 취소할 수 없다`() {
        val room = recruitingRoom()
        room.cancel()

        assertThat(room.canCancel()).isFalse()
    }

    // canCancel 판정을 건너뛴 호출은 프로그래밍 오류다(4xx 가 아니라 500).
    @Test
    fun `취소할 수 없는 룸에 cancel 을 부르면 즉시 실패한다`() {
        val room = recruitingRoom()
        room.cancel()

        assertThatThrownBy { room.cancel() }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(room.status).isEqualTo(RoomStatus.CANCELED)
    }

    // 취소와 진행 시작은 서로 다른 문이다. 한쪽이 열려 있다고 다른 쪽이 열리지 않는다.
    @Test
    fun `모집 중인 룸은 진행을 시작할 수 없고 취소된 룸도 마찬가지다`() {
        val room = recruitingRoom()
        assertThat(room.canStartProgress(now)).isFalse()

        room.cancel()

        assertThat(room.canStartProgress(now)).isFalse()
    }

    @Test
    fun `확정 룸은 예정 시각 전에는 진행을 시작할 수 없다`() {
        val room = recruitingRoom()
        room.confirm()

        assertThat(room.canStartProgress(now.plusDays(3).minusNanos(1))).isFalse()
    }

    @Test
    fun `확정 룸은 예정 시각부터 5분을 기다리지 않고 진행을 시작할 수 있다`() {
        val room = recruitingRoom()
        val startAt = now.plusDays(3)
        room.confirm()

        assertThat(room.canStartProgress(startAt)).isTrue()
        assertThat(room.canStartProgress(startAt.plusMinutes(1))).isTrue()
    }

    @Test
    fun `진행 중인 룸은 완료로 전이한다`() {
        val room = inProgressRoom()

        room.complete()

        assertThat(room.status).isEqualTo(RoomStatus.COMPLETED)
    }

    // 전원 제출 판정을 건너뛴 호출은 프로그래밍 오류다(4xx 가 아니라 500).
    @Test
    fun `진행 중이 아닌 룸에 complete 를 부르면 즉시 실패한다`() {
        val notInProgress = listOf(
            recruitingRoom(),
            recruitingRoom().apply { confirm() },
            recruitingRoom().apply { cancel() },
            inProgressRoom().apply { complete() },
        )

        notInProgress.forEach { room ->
            val statusBefore = room.status
            assertThatThrownBy { room.complete() }
                .isInstanceOf(IllegalStateException::class.java)
            assertThat(room.status).isEqualTo(statusBefore)
        }
    }

    private fun inProgressRoom(): RoomEntity = recruitingRoom().apply {
        confirm()
        startProgress(now.plusDays(3))
    }
}
