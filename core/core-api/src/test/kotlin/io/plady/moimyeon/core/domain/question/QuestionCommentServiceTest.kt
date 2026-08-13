package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class QuestionCommentServiceTest {
    private val accessValidator = mockk<QuestionCommentAccessValidator>()
    private val commentManager = mockk<QuestionCommentManager>()
    private val commentReader = mockk<QuestionCommentReader>()
    private val clock = Clock.fixed(Instant.parse("2026-08-13T13:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val service = QuestionCommentService(accessValidator, commentManager, commentReader, clock)

    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val questionId = 1L
    private val commentId = 2L

    @Test
    fun `클라이언트가 보낸 MEMO 유형으로 원 질문에 댓글을 남긴다`() {
        justRun { accessValidator.validateWriter(roomId, authorMemberId, intervieweeMemberId) }
        every {
            commentManager.record(
                roomId,
                intervieweeMemberId,
                questionId,
                authorMemberId,
                QuestionCommentType.MEMO,
                "다음 꼬리질문은 배치 주기로 이어가기",
            )
        } returns commentId

        val result = service.leaveComment(
            authorMemberId,
            roomId,
            intervieweeMemberId,
            questionId,
            QuestionCommentType.MEMO,
            "다음 꼬리질문은 배치 주기로 이어가기",
        )

        assertThat(result).isEqualTo(commentId)
        verifyOrder {
            accessValidator.validateWriter(roomId, authorMemberId, intervieweeMemberId)
            commentManager.record(
                roomId,
                intervieweeMemberId,
                questionId,
                authorMemberId,
                QuestionCommentType.MEMO,
                "다음 꼬리질문은 배치 주기로 이어가기",
            )
        }
    }

    @Test
    fun `좋아요 버튼을 누르면 작성한 댓글을 GOOD_POINT로 변경한다`() {
        givenCanWrite()
        justRun {
            commentManager.toggleType(
                roomId,
                intervieweeMemberId,
                questionId,
                commentId,
                authorMemberId,
                QuestionCommentType.GOOD_POINT,
            )
        }

        service.toggleType(
            authorMemberId,
            roomId,
            intervieweeMemberId,
            questionId,
            commentId,
            QuestionCommentType.GOOD_POINT,
        )

        verifyOrder {
            accessValidator.validateWriter(roomId, authorMemberId, intervieweeMemberId)
            commentManager.toggleType(
                roomId,
                intervieweeMemberId,
                questionId,
                commentId,
                authorMemberId,
                QuestionCommentType.GOOD_POINT,
            )
        }
    }

    @Test
    fun `싫어요 버튼을 누르면 작성한 댓글을 IMPROVEMENT_POINT로 변경한다`() {
        givenCanWrite()
        justRun {
            commentManager.toggleType(
                roomId,
                intervieweeMemberId,
                questionId,
                commentId,
                authorMemberId,
                QuestionCommentType.IMPROVEMENT_POINT,
            )
        }

        service.toggleType(
            authorMemberId,
            roomId,
            intervieweeMemberId,
            questionId,
            commentId,
            QuestionCommentType.IMPROVEMENT_POINT,
        )

        verify {
            commentManager.toggleType(
                roomId,
                intervieweeMemberId,
                questionId,
                commentId,
                authorMemberId,
                QuestionCommentType.IMPROVEMENT_POINT,
            )
        }
    }

    @Test
    fun `작성자는 진행 중 댓글 본문을 수정한다`() {
        givenCanWrite()
        justRun {
            commentManager.edit(
                roomId,
                intervieweeMemberId,
                questionId,
                commentId,
                authorMemberId,
                "보상 트랜잭션 설명이 구체적이었어요",
            )
        }

        service.editComment(
            authorMemberId,
            roomId,
            intervieweeMemberId,
            questionId,
            commentId,
            "보상 트랜잭션 설명이 구체적이었어요",
        )

        verifyOrder {
            accessValidator.validateWriter(roomId, authorMemberId, intervieweeMemberId)
            commentManager.edit(
                roomId,
                intervieweeMemberId,
                questionId,
                commentId,
                authorMemberId,
                "보상 트랜잭션 설명이 구체적이었어요",
            )
        }
    }

    @Test
    fun `작성자는 진행 중 댓글을 삭제한다`() {
        givenCanWrite()
        justRun {
            commentManager.remove(
                roomId,
                intervieweeMemberId,
                questionId,
                commentId,
                authorMemberId,
                LocalDateTime.of(2026, 8, 13, 22, 0),
            )
        }

        service.deleteComment(
            authorMemberId,
            roomId,
            intervieweeMemberId,
            questionId,
            commentId,
        )

        verifyOrder {
            accessValidator.validateWriter(roomId, authorMemberId, intervieweeMemberId)
            commentManager.remove(
                roomId,
                intervieweeMemberId,
                questionId,
                commentId,
                authorMemberId,
                LocalDateTime.of(2026, 8, 13, 22, 0),
            )
        }
    }

    @Test
    fun `룸 종료 후 면접자도 댓글을 20개 단위로 조회한다`() {
        val cursor = QuestionCommentCursor(
            createdAt = LocalDateTime.of(2026, 8, 13, 21, 0),
            id = 20L,
        )
        val page = QuestionCommentPage(comments = emptyList(), nextCursor = null)
        justRun { accessValidator.validateViewer(roomId, intervieweeMemberId, intervieweeMemberId) }
        every {
            commentReader.getPage(roomId, intervieweeMemberId, questionId, cursor)
        } returns page

        val result = service.getComments(
            intervieweeMemberId,
            roomId,
            intervieweeMemberId,
            questionId,
            cursor,
        )

        assertThat(result).isEqualTo(page)
        verifyOrder {
            accessValidator.validateViewer(roomId, intervieweeMemberId, intervieweeMemberId)
            commentReader.getPage(roomId, intervieweeMemberId, questionId, cursor)
        }
    }

    @Test
    fun `댓글 쓰기 권한이 없으면 수정과 삭제를 실행하지 않는다`() {
        every {
            accessValidator.validateWriter(roomId, authorMemberId, intervieweeMemberId)
        } throws CoreException(CoreErrorType.QUESTION_COMMENT_NOT_EDITABLE)

        assertThatThrownBy {
            service.editComment(
                authorMemberId,
                roomId,
                intervieweeMemberId,
                questionId,
                commentId,
                "종료 후 수정",
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_COMMENT_NOT_EDITABLE)
        }

        verify(exactly = 0) {
            commentManager.edit(any(), any(), any(), any(), any(), any())
            commentManager.remove(any(), any(), any(), any(), any(), any())
        }
    }

    private fun givenCanWrite() {
        justRun { accessValidator.validateWriter(roomId, authorMemberId, intervieweeMemberId) }
    }
}
