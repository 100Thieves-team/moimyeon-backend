package io.plady.moimyeon.core.domain.round

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.progress.RoomProgressAccessValidator
import io.plady.moimyeon.core.domain.question.QuestionCardSet
import io.plady.moimyeon.core.domain.question.QuestionCardSetAccessValidator
import io.plady.moimyeon.core.domain.question.QuestionCardSetReader
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class RoundServiceTest {
    private val progressAccessValidator = mockk<RoomProgressAccessValidator>()
    private val questionCardSetAccessValidator = mockk<QuestionCardSetAccessValidator>()
    private val questionCardSetReader = mockk<QuestionCardSetReader>()
    private val service = RoundService(
        progressAccessValidator,
        questionCardSetAccessValidator,
        questionCardSetReader,
    )

    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val participantMemberId = UUID.randomUUID()
    private val outsiderMemberId = UUID.randomUUID()

    @Test
    fun `면접자가 자신의 라운드를 조회하면 질문 카드셋을 읽지 않고 면접자 화면을 본다`() {
        justRun { progressAccessValidator.validateInProgressParticipant(roomId, intervieweeMemberId) }

        val result = service.getScreen(intervieweeMemberId, roomId, intervieweeMemberId)

        assertThat(result).isEqualTo(RoundScreen.Interviewee(intervieweeMemberId))
        verifyOrder {
            progressAccessValidator.validateInProgressParticipant(roomId, intervieweeMemberId)
        }
        verify(exactly = 0) {
            questionCardSetAccessValidator.validateOtherCardSetTarget(any(), any(), any())
            questionCardSetReader.getByRoomAndTarget(any(), any())
        }
    }

    @Test
    fun `면접자 외 참여자는 선택한 라운드 대상의 질문 카드셋을 본다`() {
        val cardSet = QuestionCardSet(
            targetMemberId = intervieweeMemberId,
            questions = emptyList(),
        )
        justRun { progressAccessValidator.validateInProgressParticipant(roomId, participantMemberId) }
        justRun {
            questionCardSetAccessValidator.validateOtherCardSetTarget(
                roomId,
                participantMemberId,
                intervieweeMemberId,
            )
        }
        every {
            questionCardSetReader.getByRoomAndTarget(roomId, intervieweeMemberId)
        } returns cardSet

        val result = service.getScreen(participantMemberId, roomId, intervieweeMemberId)

        assertThat(result).isEqualTo(
            RoundScreen.Participant(
                intervieweeMemberId = intervieweeMemberId,
                questionCardSet = cardSet,
            ),
        )
        verifyOrder {
            progressAccessValidator.validateInProgressParticipant(roomId, participantMemberId)
            questionCardSetAccessValidator.validateOtherCardSetTarget(
                roomId,
                participantMemberId,
                intervieweeMemberId,
            )
            questionCardSetReader.getByRoomAndTarget(roomId, intervieweeMemberId)
        }
    }

    @Test
    fun `라운드 대상이 바뀌면 같은 참여자의 면접자 화면과 카드셋 노출이 뒤집힌다`() {
        val cardSet = QuestionCardSet(
            targetMemberId = intervieweeMemberId,
            questions = emptyList(),
        )
        justRun { progressAccessValidator.validateInProgressParticipant(roomId, participantMemberId) }
        justRun {
            questionCardSetAccessValidator.validateOtherCardSetTarget(
                roomId,
                participantMemberId,
                intervieweeMemberId,
            )
        }
        every {
            questionCardSetReader.getByRoomAndTarget(roomId, intervieweeMemberId)
        } returns cardSet

        val myRound = service.getScreen(participantMemberId, roomId, participantMemberId)
        val otherRound = service.getScreen(participantMemberId, roomId, intervieweeMemberId)

        assertThat(myRound).isEqualTo(RoundScreen.Interviewee(participantMemberId))
        assertThat(otherRound).isEqualTo(
            RoundScreen.Participant(
                intervieweeMemberId = intervieweeMemberId,
                questionCardSet = cardSet,
            ),
        )
        verify(exactly = 2) {
            progressAccessValidator.validateInProgressParticipant(roomId, participantMemberId)
        }
        verify(exactly = 1) {
            questionCardSetReader.getByRoomAndTarget(roomId, intervieweeMemberId)
        }
    }

    @Test
    fun `진행 접근 검증에 실패하면 라운드 대상과 질문 카드셋을 조회하지 않는다`() {
        every {
            progressAccessValidator.validateInProgressParticipant(roomId, outsiderMemberId)
        } throws CoreException(CoreErrorType.ROOM_PROGRESS_FORBIDDEN)

        assertThatThrownBy {
            service.getScreen(outsiderMemberId, roomId, intervieweeMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_FORBIDDEN)
        }

        verify(exactly = 0) {
            questionCardSetAccessValidator.validateOtherCardSetTarget(any(), any(), any())
            questionCardSetReader.getByRoomAndTarget(any(), any())
        }
    }

    @Test
    fun `라운드 대상 검증에 실패하면 질문 카드셋을 조회하지 않는다`() {
        justRun { progressAccessValidator.validateInProgressParticipant(roomId, participantMemberId) }
        every {
            questionCardSetAccessValidator.validateOtherCardSetTarget(
                roomId,
                participantMemberId,
                intervieweeMemberId,
            )
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_NOT_FOUND)

        assertThatThrownBy {
            service.getScreen(participantMemberId, roomId, intervieweeMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_NOT_FOUND)
        }

        verify(exactly = 0) {
            questionCardSetReader.getByRoomAndTarget(any(), any())
        }
    }
}
