package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.enums.RoomStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// MOI-398(진행 확정)이 락 안에서 같은 판정을 재사용한다. 판정이 갈리면 F1 버튼 상태와 실제 결과가 어긋나므로
// 여기서 경계와 우선순위를 고정한다.
class RoomConfirmationTest {
    private val now = LocalDateTime.of(2026, 8, 11, 10, 0)
    private val future = now.plusDays(3)
    private val past = now.minusDays(1)

    @Test
    fun `인원이 최소 진행 인원 이상이고 일정이 남았으면 확정할 수 있다`() {
        val confirmation = confirmationOf(currentParticipants = 4, min = 3, startAt = future)

        assertThat(confirmation.ready).isTrue()
    }

    @Test
    fun `인원이 최소 진행 인원과 같으면 확정할 수 있다`() {
        val confirmation = confirmationOf(currentParticipants = 3, min = 3, startAt = future)

        assertThat(confirmation.ready).isTrue()
    }

    @Test
    fun `확정할 수 있으면 사유가 없다`() {
        val confirmation = confirmationOf(currentParticipants = 3, min = 3, startAt = future)

        assertThat(confirmation.blockReason).isNull()
    }

    @Test
    fun `인원이 최소 진행 인원에 못 미치면 인원 미달이 사유다`() {
        val confirmation = confirmationOf(currentParticipants = 2, min = 3, startAt = future)

        assertThat(confirmation.ready).isFalse()
        assertThat(confirmation.blockReason).isEqualTo(RoomConfirmationBlockReason.BELOW_MIN_CAPACITY)
    }

    @Test
    fun `일정이 지났으면 인원이 충분해도 일정 경과가 사유다`() {
        val confirmation = confirmationOf(currentParticipants = 4, min = 3, startAt = past)

        assertThat(confirmation.ready).isFalse()
        assertThat(confirmation.blockReason).isEqualTo(RoomConfirmationBlockReason.SCHEDULE_PASSED)
    }

    // 인원과 일정이 함께 어긋나도 일정이 먼저다. 사유가 구현 순서에 따라 흔들리지 않게 고정한다.
    @Test
    fun `인원도 미달이고 일정도 지났으면 일정 경과가 사유다`() {
        val confirmation = confirmationOf(currentParticipants = 2, min = 3, startAt = past)

        assertThat(confirmation.blockReason).isEqualTo(RoomConfirmationBlockReason.SCHEDULE_PASSED)
    }

    @Test
    fun `모집 중이 아닌 룸은 인원과 일정을 따지지 않고 룸 상태가 사유다`() {
        val blockReasonByStatus = mapOf(
            RoomStatus.CONFIRMED to RoomConfirmationBlockReason.ROOM_CONFIRMED,
            RoomStatus.IN_PROGRESS to RoomConfirmationBlockReason.ROOM_IN_PROGRESS,
            RoomStatus.COMPLETED to RoomConfirmationBlockReason.ROOM_COMPLETED,
            RoomStatus.CANCELED to RoomConfirmationBlockReason.ROOM_CANCELED,
        )

        // 모집 중이 아닌 상태를 전부 돈다. 상태가 늘면 getValue 가 터져 이 테스트가 먼저 갱신을 요구한다.
        (RoomStatus.entries - RoomStatus.RECRUITING).forEach { status ->
            val expected = blockReasonByStatus.getValue(status)
            // 인원과 일정은 모두 충족시켜 둔다. 룸 상태가 먼저 걸리는지만 본다.
            val confirmation = confirmationOf(
                currentParticipants = 4,
                min = 3,
                startAt = future,
                status = status,
            )

            assertThat(confirmation.ready).isFalse()
            assertThat(confirmation.blockReason).isEqualTo(expected)
        }
    }

    private fun confirmationOf(
        currentParticipants: Int,
        min: Int,
        startAt: LocalDateTime,
        status: RoomStatus = RoomStatus.RECRUITING,
    ): RoomConfirmation {
        val detail = RoomDetail(
            room = room(min = min, startAt = startAt, status = status),
            hostMemberId = UUID.randomUUID(),
            currentParticipants = currentParticipants,
        )
        return RoomConfirmation.of(detail, now)
    }

    private fun room(min: Int, startAt: LocalDateTime, status: RoomStatus): Room {
        return Room(
            id = UUID.randomUUID(),
            jobPostingId = 1L,
            jobRoleId = 1L,
            title = RoomTitle("카카오 백엔드 2차 대비"),
            description = null,
            interviewStage = InterviewStage.SECOND,
            interviewType = InterviewType.JOB,
            meetingPlace = MeetingPlace.Online,
            capacity = RoomCapacity(min = min, max = 8),
            schedule = RoomSchedule(startAt = startAt, durationMinutes = 90),
            resumeSharingPolicy = ResumeSharingPolicy.AI_SUMMARY_ONLY,
            status = status,
        )
    }
}
