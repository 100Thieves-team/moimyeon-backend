package io.plady.moimyeon.core.domain.roundfeedback

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoundFeedbackServiceTest {
    private val accessValidator = mockk<RoundFeedbackAccessValidator>()
    private val feedbackReader = mockk<RoundFeedbackReader>()
    private val feedbackManager = mockk<RoundFeedbackManager>()
    private val service = RoundFeedbackService(accessValidator, feedbackReader, feedbackManager)

    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val participantMemberId = UUID.randomUUID()
    private val otherParticipantMemberId = UUID.randomUUID()
    private val feedbackId = 31L
    private val otherFeedbackId = 32L

    @Test
    fun `참여자는 선택한 라운드에서 자신이 남긴 질문 메모를 질문별로 조회한다`() {
        val records = listOf(
            RoundQuestionRecord(
                questionId = 11L,
                questionContent = "장애 원인을 어떻게 좁혔나요?",
                comments = listOf(
                    RoundQuestionComment(
                        id = 21L,
                        type = QuestionCommentType.GOOD_POINT,
                        content = "장애 시나리오부터 교차 시작한 점이 좋았어요",
                        createdAt = LocalDateTime.of(2026, 8, 14, 10, 0),
                    ),
                ),
            ),
            RoundQuestionRecord(
                questionId = 12L,
                questionContent = "보상 트랜잭션은 어떻게 설계했나요?",
                comments = listOf(
                    RoundQuestionComment(
                        id = 22L,
                        type = QuestionCommentType.IMPROVEMENT_POINT,
                        content = "구체적인 실패 사례가 더 필요해요",
                        createdAt = LocalDateTime.of(2026, 8, 14, 10, 1),
                    ),
                ),
            ),
        )
        justRun {
            accessValidator.validateOtherParticipantWriter(roomId, participantMemberId, intervieweeMemberId)
        }
        every {
            feedbackReader.getMyQuestionRecords(roomId, intervieweeMemberId, participantMemberId)
        } returns records

        val result = service.getMyQuestionRecords(participantMemberId, roomId, intervieweeMemberId)

        assertThat(result).containsExactlyElementsOf(records)
        verifyOrder {
            accessValidator.validateOtherParticipantWriter(roomId, participantMemberId, intervieweeMemberId)
            feedbackReader.getMyQuestionRecords(roomId, intervieweeMemberId, participantMemberId)
        }
    }

    @Test
    fun `같은 작성자의 기록도 선택한 면접자 라운드의 것만 조회한다`() {
        val otherIntervieweeMemberId = UUID.randomUUID()
        justRun {
            accessValidator.validateOtherParticipantWriter(roomId, participantMemberId, intervieweeMemberId)
        }
        every {
            feedbackReader.getMyQuestionRecords(roomId, intervieweeMemberId, participantMemberId)
        } returns emptyList()

        service.getMyQuestionRecords(participantMemberId, roomId, intervieweeMemberId)

        verify(exactly = 1) {
            feedbackReader.getMyQuestionRecords(roomId, intervieweeMemberId, participantMemberId)
        }
        verify(exactly = 0) {
            feedbackReader.getMyQuestionRecords(roomId, otherIntervieweeMemberId, participantMemberId)
        }
    }

    @Test
    fun `면접자 외 참여자는 선택한 라운드에 최종 피드백을 작성자별 한 건으로 저장한다`() {
        val command = RoundFeedbackCommand(
            roomId = roomId,
            intervieweeMemberId = intervieweeMemberId,
            authorMemberId = participantMemberId,
            type = RoundFeedbackType.FINAL,
            content = "설명 구조가 탄탄했지만 실제 장애 사례를 수치와 함께 준비하면 더 좋아요",
        )
        justRun {
            accessValidator.validateOtherParticipantWriter(roomId, participantMemberId, intervieweeMemberId)
        }
        every { feedbackManager.save(command) } returns feedbackId

        val result = service.leaveFinalFeedback(
            participantMemberId,
            roomId,
            intervieweeMemberId,
            command.content,
        )

        assertThat(result).isEqualTo(feedbackId)
        verifyOrder {
            accessValidator.validateOtherParticipantWriter(roomId, participantMemberId, intervieweeMemberId)
            feedbackManager.save(command)
        }
    }

    @Test
    fun `같은 작성자가 같은 라운드에 최종 피드백을 다시 등록하면 수정하지 않고 거부한다`() {
        val command = RoundFeedbackCommand(
            roomId = roomId,
            intervieweeMemberId = intervieweeMemberId,
            authorMemberId = participantMemberId,
            type = RoundFeedbackType.FINAL,
            content = "두 번째 등록은 기존 피드백을 덮어쓰지 않아요",
        )
        justRun {
            accessValidator.validateOtherParticipantWriter(roomId, participantMemberId, intervieweeMemberId)
        }
        every {
            feedbackManager.save(command)
        } throws CoreException(CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS)

        assertThatThrownBy {
            service.leaveFinalFeedback(
                participantMemberId,
                roomId,
                intervieweeMemberId,
                command.content,
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS)
        }
    }

    @Test
    fun `면접자는 자신의 라운드에 자가 피드백을 저장한다`() {
        val command = RoundFeedbackCommand(
            roomId = roomId,
            intervieweeMemberId = intervieweeMemberId,
            authorMemberId = intervieweeMemberId,
            type = RoundFeedbackType.SELF,
            content = "복구 전략을 구체적인 장애 사례로 설명하지 못한 점이 아쉬웠어요",
        )
        justRun {
            accessValidator.validateIntervieweeWriter(roomId, intervieweeMemberId, intervieweeMemberId)
        }
        every { feedbackManager.save(command) } returns feedbackId

        val result = service.leaveSelfFeedback(
            intervieweeMemberId,
            roomId,
            intervieweeMemberId,
            command.content,
        )

        assertThat(result).isEqualTo(feedbackId)
        verifyOrder {
            accessValidator.validateIntervieweeWriter(roomId, intervieweeMemberId, intervieweeMemberId)
            feedbackManager.save(command)
        }
    }

    @Test
    fun `면접자는 같은 라운드의 자가 피드백 본문을 수정한다`() {
        val initialCommand = RoundFeedbackCommand(
            roomId = roomId,
            intervieweeMemberId = intervieweeMemberId,
            authorMemberId = intervieweeMemberId,
            type = RoundFeedbackType.SELF,
            content = "복구 전략 답변이 아쉬웠어요",
        )
        val revisedCommand = RoundFeedbackCommand(
            roomId = roomId,
            intervieweeMemberId = intervieweeMemberId,
            authorMemberId = intervieweeMemberId,
            type = RoundFeedbackType.SELF,
            content = "복구 전략을 실제 장애 수치로 설명하지 못한 점이 아쉬웠어요",
        )
        justRun {
            accessValidator.validateIntervieweeWriter(roomId, intervieweeMemberId, intervieweeMemberId)
        }
        every { feedbackManager.save(initialCommand) } returns feedbackId
        every { feedbackManager.save(revisedCommand) } returns feedbackId

        service.leaveSelfFeedback(
            intervieweeMemberId,
            roomId,
            intervieweeMemberId,
            initialCommand.content,
        )
        val result = service.leaveSelfFeedback(
            intervieweeMemberId,
            roomId,
            intervieweeMemberId,
            revisedCommand.content,
        )

        assertThat(result).isEqualTo(feedbackId)
        verifyOrder {
            accessValidator.validateIntervieweeWriter(roomId, intervieweeMemberId, intervieweeMemberId)
            feedbackManager.save(initialCommand)
            accessValidator.validateIntervieweeWriter(roomId, intervieweeMemberId, intervieweeMemberId)
            feedbackManager.save(revisedCommand)
        }
    }

    @Test
    fun `룸 종료 여부와 관계없이 열람을 확인하지 않은 최종 피드백 내용은 가린다`() {
        val view = IntervieweeRoundFeedback(
            selfFeedback = SelfFeedback(
                id = 30L,
                content = "복구 전략 답변이 아쉬웠어요",
            ),
            finalFeedbacks = listOf(
                FinalFeedbackCard(
                    id = feedbackId,
                    author = RoundFeedbackAuthor(
                        memberId = participantMemberId,
                        displayName = "튼튼한 곰",
                        role = RoundFeedbackAuthorRole.PARTICIPANT,
                    ),
                    content = null,
                    revealed = false,
                ),
                FinalFeedbackCard(
                    id = otherFeedbackId,
                    author = RoundFeedbackAuthor(
                        memberId = otherParticipantMemberId,
                        displayName = "탈퇴 회원",
                        role = RoundFeedbackAuthorRole.PARTICIPANT,
                    ),
                    content = null,
                    revealed = false,
                ),
            ),
        )
        justRun {
            accessValidator.validateIntervieweeViewer(roomId, intervieweeMemberId, intervieweeMemberId)
        }
        every {
            feedbackReader.getIntervieweeFeedback(roomId, intervieweeMemberId)
        } returns view

        val result = service.getIntervieweeFeedback(intervieweeMemberId, roomId, intervieweeMemberId)

        assertThat(result.finalFeedbacks).allSatisfy {
            assertThat(it.revealed).isFalse()
            assertThat(it.content).isNull()
        }
        assertThat(result.finalFeedbacks.map { it.author.displayName })
            .containsExactly("튼튼한 곰", "탈퇴 회원")
        assertThat(result.finalFeedbacks.map { it.author.role })
            .containsOnly(RoundFeedbackAuthorRole.PARTICIPANT)
        verifyOrder {
            accessValidator.validateIntervieweeViewer(roomId, intervieweeMemberId, intervieweeMemberId)
            feedbackReader.getIntervieweeFeedback(roomId, intervieweeMemberId)
        }
    }

    @Test
    fun `면접자가 한 최종 피드백의 열람을 확인하면 선택한 카드만 공개 처리한다`() {
        justRun {
            accessValidator.validateIntervieweeViewer(roomId, intervieweeMemberId, intervieweeMemberId)
        }
        justRun {
            feedbackManager.confirmDisclosure(roomId, intervieweeMemberId, feedbackId)
        }

        service.confirmFinalFeedbackDisclosure(
            intervieweeMemberId,
            roomId,
            intervieweeMemberId,
            feedbackId,
        )

        verifyOrder {
            accessValidator.validateIntervieweeViewer(roomId, intervieweeMemberId, intervieweeMemberId)
            feedbackManager.confirmDisclosure(roomId, intervieweeMemberId, feedbackId)
        }
        verify(exactly = 0) {
            feedbackManager.confirmDisclosure(roomId, intervieweeMemberId, otherFeedbackId)
        }
    }

    @Test
    fun `이미 공개한 최종 피드백 카드를 다시 확인해도 성공한다`() {
        justRun {
            accessValidator.validateIntervieweeViewer(roomId, intervieweeMemberId, intervieweeMemberId)
        }
        justRun {
            feedbackManager.confirmDisclosure(roomId, intervieweeMemberId, feedbackId)
        }

        service.confirmFinalFeedbackDisclosure(
            intervieweeMemberId,
            roomId,
            intervieweeMemberId,
            feedbackId,
        )
        service.confirmFinalFeedbackDisclosure(
            intervieweeMemberId,
            roomId,
            intervieweeMemberId,
            feedbackId,
        )

        verify(exactly = 2) {
            feedbackManager.confirmDisclosure(roomId, intervieweeMemberId, feedbackId)
        }
    }

    @Test
    fun `피드백 참여 권한 검증이 실패하면 기록을 읽거나 최종 피드백을 저장하지 않는다`() {
        every {
            accessValidator.validateOtherParticipantWriter(roomId, participantMemberId, intervieweeMemberId)
        } throws CoreException(CoreErrorType.ROOM_PROGRESS_FORBIDDEN)

        assertThatThrownBy {
            service.leaveFinalFeedback(
                participantMemberId,
                roomId,
                intervieweeMemberId,
                "저장되면 안 되는 피드백",
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_FORBIDDEN)
        }

        verify(exactly = 0) {
            feedbackReader.getMyQuestionRecords(any(), any(), any())
            feedbackManager.save(any())
        }
    }
}
