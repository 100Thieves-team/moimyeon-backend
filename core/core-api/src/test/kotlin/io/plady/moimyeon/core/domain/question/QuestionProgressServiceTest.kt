package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.progress.RoomProgressAccessValidator
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestionProgressServiceTest {
    private val progressAccessValidator = mockk<RoomProgressAccessValidator>()
    private val cardSetAccessValidator = mockk<QuestionCardSetAccessValidator>()
    private val questionUsageMarker = mockk<QuestionUsageMarker>()
    private val questionRecorder = mockk<QuestionRecorder>()
    private val service = QuestionProgressService(
        progressAccessValidator,
        cardSetAccessValidator,
        questionUsageMarker,
        questionRecorder,
    )

    private val roomId = UUID.randomUUID()
    private val actorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val questionId = 1L

    @Test
    fun `면접자 외 확정 참여자는 진행 중 질문을 선택한 대상에게 추가한다`() {
        givenParticipantCanUseTargetCardSet()
        every {
            questionRecorder.record(
                roomId,
                targetMemberId,
                actorMemberId,
                null,
                "장애 원인을 어떻게 좁혔나요?",
                QuestionSource.IN_PROGRESS,
            )
        } returns 2L

        val result = service.leaveQuestion(
            actorMemberId,
            roomId,
            targetMemberId,
            "장애 원인을 어떻게 좁혔나요?",
        )

        assertThat(result).isEqualTo(2L)
        verifyOrder {
            progressAccessValidator.validateRailViewer(roomId, actorMemberId)
            cardSetAccessValidator.validateOtherCardSetTarget(roomId, actorMemberId, targetMemberId)
            questionRecorder.record(
                roomId,
                targetMemberId,
                actorMemberId,
                null,
                "장애 원인을 어떻게 좁혔나요?",
                QuestionSource.IN_PROGRESS,
            )
        }
    }

    @Test
    fun `진행 중 꼬리질문도 선택한 대상의 원 질문에 추가한다`() {
        givenParticipantCanUseTargetCardSet()
        every {
            questionRecorder.record(
                roomId,
                targetMemberId,
                actorMemberId,
                questionId,
                "실시간 검증으로 바꾼다면 어디부터 손대나요?",
                QuestionSource.IN_PROGRESS,
            )
        } returns 2L

        val result = service.leaveFollowUp(
            actorMemberId,
            roomId,
            targetMemberId,
            questionId,
            "실시간 검증으로 바꾼다면 어디부터 손대나요?",
        )

        assertThat(result).isEqualTo(2L)
        verifyOrder {
            progressAccessValidator.validateRailViewer(roomId, actorMemberId)
            cardSetAccessValidator.validateOtherCardSetTarget(roomId, actorMemberId, targetMemberId)
            questionRecorder.record(
                roomId,
                targetMemberId,
                actorMemberId,
                questionId,
                "실시간 검증으로 바꾼다면 어디부터 손대나요?",
                QuestionSource.IN_PROGRESS,
            )
        }
    }

    @Test
    fun `면접자가 자신의 라운드에 진행 중 질문을 추가하려 하면 저장하지 않는다`() {
        justRun { progressAccessValidator.validateRailViewer(roomId, targetMemberId) }
        every {
            cardSetAccessValidator.validateOtherCardSetTarget(roomId, targetMemberId, targetMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)

        assertThatThrownBy {
            service.leaveQuestion(targetMemberId, roomId, targetMemberId, "셀프 질문")
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)
        }

        verify(exactly = 0) { questionRecorder.record(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `면접자 외 확정 참여자는 선택한 대상의 질문을 질문했음으로 표시한다`() {
        givenParticipantCanUseTargetCardSet()
        justRun { questionUsageMarker.markAsked(roomId, targetMemberId, questionId) }

        service.markAsked(actorMemberId, roomId, targetMemberId, questionId)

        verifyOrder {
            progressAccessValidator.validateRailViewer(roomId, actorMemberId)
            cardSetAccessValidator.validateOtherCardSetTarget(roomId, actorMemberId, targetMemberId)
            questionUsageMarker.markAsked(roomId, targetMemberId, questionId)
        }
    }

    @Test
    fun `면접자가 자신의 질문을 표시하려 하면 변경하지 않고 E1502 를 던진다`() {
        justRun { progressAccessValidator.validateRailViewer(roomId, targetMemberId) }
        every {
            cardSetAccessValidator.validateOtherCardSetTarget(roomId, targetMemberId, targetMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)

        assertThatThrownBy {
            service.markAsked(targetMemberId, roomId, targetMemberId, questionId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)
        }

        verify(exactly = 0) { questionUsageMarker.markAsked(any(), any(), any()) }
    }

    @Test
    fun `진행 접근이 거부되면 대상 검증과 질문 변경을 실행하지 않는다`() {
        every {
            progressAccessValidator.validateRailViewer(roomId, actorMemberId)
        } throws CoreException(CoreErrorType.ROOM_PROGRESS_FORBIDDEN)

        assertThatThrownBy {
            service.markAsked(actorMemberId, roomId, targetMemberId, questionId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_FORBIDDEN)
        }

        verify(exactly = 0) {
            cardSetAccessValidator.validateOtherCardSetTarget(any(), any(), any())
            questionUsageMarker.markAsked(any(), any(), any())
        }
    }

    @Test
    fun `선택한 대상이 유효하지 않으면 질문을 변경하지 않는다`() {
        justRun { progressAccessValidator.validateRailViewer(roomId, actorMemberId) }
        every {
            cardSetAccessValidator.validateOtherCardSetTarget(roomId, actorMemberId, targetMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_NOT_FOUND)

        assertThatThrownBy {
            service.markAsked(actorMemberId, roomId, targetMemberId, questionId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_NOT_FOUND)
        }

        verify(exactly = 0) { questionUsageMarker.markAsked(any(), any(), any()) }
    }

    private fun givenParticipantCanUseTargetCardSet() {
        justRun { progressAccessValidator.validateRailViewer(roomId, actorMemberId) }
        justRun {
            cardSetAccessValidator.validateOtherCardSetTarget(roomId, actorMemberId, targetMemberId)
        }
    }
}
