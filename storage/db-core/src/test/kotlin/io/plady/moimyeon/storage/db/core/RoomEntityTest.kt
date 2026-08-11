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

// 메모리에서 도달할 수 있는 상태는 RECRUITING 과 CANCELED 둘뿐이다.
// CONFIRMED·IN_PROGRESS·COMPLETED 로 만드는 전이가 없어(확정은 MOI-398) 그쪽 판정은
// 실제 DB 상태를 만들 수 있는 RoomCancellationIT 가 본다.
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
        assertThat(room.canStartProgress()).isFalse()

        room.cancel()

        assertThat(room.canStartProgress()).isFalse()
    }
}
