package io.plady.moimyeon.core.domain.roundfeedback

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class RoundFeedbackAccessValidatorTest {
    private val roomFinder = mockk<RoomFinder>()
    private val participationFinder = mockk<ParticipationFinder>()
    private val validator = RoundFeedbackAccessValidator(roomFinder, participationFinder)
    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val participantMemberId = UUID.randomUUID()

    @Test
    fun `진행 중 확정 참여자는 다른 면접자의 피드백을 작성할 수 있다`() {
        roomStatus(RoomStatus.IN_PROGRESS)
        confirmed(participantMemberId, intervieweeMemberId)

        assertThatCode {
            validator.validateOtherParticipantWriter(roomId, participantMemberId, intervieweeMemberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `완료된 룸에서도 면접자는 자기 피드백 카드를 조회하고 열람 확인할 수 있다`() {
        roomStatus(RoomStatus.COMPLETED)
        confirmed(intervieweeMemberId)

        assertThatCode {
            validator.validateIntervieweeViewer(roomId, intervieweeMemberId, intervieweeMemberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `완료된 룸에서는 자가 피드백을 수정할 수 없다`() {
        roomStatus(RoomStatus.COMPLETED)

        assertThatThrownBy {
            validator.validateIntervieweeWriter(roomId, intervieweeMemberId, intervieweeMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROUND_FEEDBACK_NOT_EDITABLE)
        }
    }

    @Test
    fun `면접자가 아닌 참여자는 해당 면접자의 피드백 카드를 볼 수 없다`() {
        roomStatus(RoomStatus.IN_PROGRESS)
        confirmed(participantMemberId, intervieweeMemberId)

        assertThatThrownBy {
            validator.validateIntervieweeViewer(roomId, participantMemberId, intervieweeMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROUND_FEEDBACK_FORBIDDEN)
        }
    }

    private fun roomStatus(status: RoomStatus) {
        val room = mockk<Room>()
        every { room.status } returns status
        every { roomFinder.getRoom(roomId) } returns room
    }

    private fun confirmed(vararg memberIds: UUID) {
        memberIds.forEach { memberId ->
            every { participationFinder.wasConfirmedParticipant(roomId, memberId) } returns true
        }
    }
}
