package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestionCardSetServiceTest {
    private val accessValidator = mockk<QuestionCardSetAccessValidator>()
    private val cardSetReader = mockk<QuestionCardSetReader>()
    private val service = QuestionCardSetService(accessValidator, cardSetReader)

    private val roomId = UUID.randomUUID()
    private val requesterMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val emptyTargetMemberId = UUID.randomUUID()
    private val nonParticipantMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `확정 참여자는 본인을 제외한 전원의 질문 카드셋을 빈 카드셋까지 조회한다`() {
        val cardSets = listOf(
            cardSet(targetMemberId, listOf(question())),
            cardSet(emptyTargetMemberId, emptyList()),
        )
        justRun { accessValidator.validateViewer(roomId, requesterMemberId) }
        every {
            cardSetReader.getAllByRoomExceptTarget(roomId, requesterMemberId)
        } returns cardSets

        val result = service.getCardSets(requesterMemberId, roomId)

        assertThat(result).containsExactlyElementsOf(cardSets)
        assertThat(result.map { it.targetMemberId })
            .containsExactly(targetMemberId, emptyTargetMemberId)
            .doesNotContain(requesterMemberId)
        assertThat(result.last().questions).isEmpty()
        verifyOrder {
            accessValidator.validateViewer(roomId, requesterMemberId)
            cardSetReader.getAllByRoomExceptTarget(roomId, requesterMemberId)
        }
    }

    @Test
    fun `확정 참여자 검증에 실패하면 카드셋 목록을 조회하지 않는다`() {
        every {
            accessValidator.validateViewer(roomId, requesterMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)

        assertThatThrownBy { service.getCardSets(requesterMemberId, roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)
            }

        verify(exactly = 0) { cardSetReader.getAllByRoomExceptTarget(any(), any()) }
    }

    @Test
    fun `다른 확정 참여자의 카드셋을 조회하면 작성자 출처 꼬리질문을 함께 받는다`() {
        val cardSet = cardSet(targetMemberId, listOf(question()))
        justRun { accessValidator.validateViewer(roomId, requesterMemberId) }
        justRun {
            accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
        }
        every { cardSetReader.getByRoomAndTarget(roomId, targetMemberId) } returns cardSet

        val result = service.getCardSet(requesterMemberId, roomId, targetMemberId)

        assertThat(result).isEqualTo(cardSet)
        assertThat(result.questions.single().authorMemberId).isEqualTo(authorMemberId)
        assertThat(result.questions.single().source).isEqualTo(QuestionSource.PREPARATION)
        assertThat(result.questions.single().followUps.single().source).isEqualTo(QuestionSource.IN_PROGRESS)
        verifyOrder {
            accessValidator.validateViewer(roomId, requesterMemberId)
            accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
            cardSetReader.getByRoomAndTarget(roomId, targetMemberId)
        }
    }

    @Test
    fun `상세 요청자가 확정 참여자가 아니면 대상 자격과 카드셋을 조회하지 않는다`() {
        every {
            accessValidator.validateViewer(roomId, requesterMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)

        assertThatThrownBy {
            service.getCardSet(requesterMemberId, roomId, targetMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)
        }

        verify(exactly = 0) { accessValidator.validateOtherCardSetTarget(any(), any(), any()) }
        verify(exactly = 0) { cardSetReader.getByRoomAndTarget(any(), any()) }
    }

    @Test
    fun `본인 대상이면 카드셋 상세를 조회하지 않는다`() {
        justRun { accessValidator.validateViewer(roomId, requesterMemberId) }
        every {
            accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, requesterMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)

        assertThatThrownBy {
            service.getCardSet(requesterMemberId, roomId, requesterMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)
        }

        verify(exactly = 0) { cardSetReader.getByRoomAndTarget(any(), any()) }
    }

    @Test
    fun `확정 참여자가 아닌 대상이면 카드셋 상세를 조회하지 않는다`() {
        justRun { accessValidator.validateViewer(roomId, requesterMemberId) }
        every {
            accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, nonParticipantMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_NOT_FOUND)

        assertThatThrownBy {
            service.getCardSet(requesterMemberId, roomId, nonParticipantMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_NOT_FOUND)
        }

        verify(exactly = 0) { cardSetReader.getByRoomAndTarget(any(), any()) }
    }

    private fun cardSet(targetMemberId: UUID, questions: List<QuestionCard>): QuestionCardSet {
        return QuestionCardSet(
            targetMemberId = targetMemberId,
            questions = questions,
        )
    }

    private fun question(): QuestionCard {
        return QuestionCard(
            id = 1L,
            authorMemberId = authorMemberId,
            content = "프로젝트에서 가장 어려웠던 기술적 결정은 무엇이었나요?",
            source = QuestionSource.PREPARATION,
            followUps = listOf(
                FollowUpQuestion(
                    id = 2L,
                    authorMemberId = authorMemberId,
                    content = "다시 선택한다면 같은 결정을 하시겠어요?",
                    source = QuestionSource.IN_PROGRESS,
                ),
            ),
        )
    }
}
