package io.plady.moimyeon.core.domain.question

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

class QuestionCommentAccessValidatorTest {
    private val roomFinder = mockk<RoomFinder>()
    private val participationFinder = mockk<ParticipationFinder>()
    private val validator = QuestionCommentAccessValidator(roomFinder, participationFinder)

    private val roomId = UUID.randomUUID()
    private val participantMemberId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()

    @Test
    fun `진행 중 면접자 외 확정 참여자는 출석 여부와 무관하게 댓글을 작성한다`() {
        givenRoom(RoomStatus.IN_PROGRESS)
        givenConfirmed(participantMemberId, intervieweeMemberId)

        assertThatCode {
            validator.validateWriter(roomId, participantMemberId, intervieweeMemberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `진행 중 면접자는 자신의 라운드 댓글을 조회하지 못한다`() {
        givenRoom(RoomStatus.IN_PROGRESS)
        givenConfirmed(intervieweeMemberId)

        assertFails(CoreErrorType.QUESTION_COMMENT_FORBIDDEN) {
            validator.validateViewer(roomId, intervieweeMemberId, intervieweeMemberId)
        }
    }

    @Test
    fun `룸 종료 후 면접자는 자신의 라운드 댓글을 조회한다`() {
        givenRoom(RoomStatus.COMPLETED)
        givenConfirmed(intervieweeMemberId)

        assertThatCode {
            validator.validateViewer(roomId, intervieweeMemberId, intervieweeMemberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `룸 종료 후에는 댓글을 작성하거나 수정하지 못한다`() {
        givenRoom(RoomStatus.COMPLETED)

        assertFails(CoreErrorType.QUESTION_COMMENT_NOT_EDITABLE) {
            validator.validateWriter(roomId, participantMemberId, intervieweeMemberId)
        }
    }

    @Test
    fun `확정 참여자가 아니면 댓글을 조회하지 못한다`() {
        givenRoom(RoomStatus.COMPLETED)
        every {
            participationFinder.wasConfirmedParticipant(roomId, participantMemberId)
        } returns false

        assertFails(CoreErrorType.QUESTION_COMMENT_FORBIDDEN) {
            validator.validateViewer(roomId, participantMemberId, intervieweeMemberId)
        }
    }

    private fun givenRoom(status: RoomStatus) {
        every { roomFinder.getRoom(roomId) } returns mockk<Room> {
            every { this@mockk.status } returns status
        }
    }

    private fun givenConfirmed(vararg memberIds: UUID) {
        memberIds.forEach { memberId ->
            every { participationFinder.wasConfirmedParticipant(roomId, memberId) } returns true
        }
    }

    private fun assertFails(errorType: CoreErrorType, block: () -> Unit) {
        assertThatThrownBy(block)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
