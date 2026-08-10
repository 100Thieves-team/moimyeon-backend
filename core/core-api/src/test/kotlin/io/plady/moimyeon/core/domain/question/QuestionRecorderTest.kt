package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class QuestionRecorderTest {
    private val questionRepository = mockk<QuestionRepository>()
    private val recorder = QuestionRecorder(questionRepository)

    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 8, 10, 3, 0)

    @Test
    fun `준비 질문을 대상과 작성자 및 출처와 함께 기록한다`() {
        val questionSlot = slot<QuestionEntity>()
        val savedQuestion = mockk<QuestionEntity> { every { id } returns 1L }
        every { questionRepository.save(capture(questionSlot)) } returns savedQuestion

        val questionId = recorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            null,
            "가장 어려웠던 기술적 결정은 무엇이었나요?",
            QuestionSource.PREPARATION,
        )

        assertThat(questionId).isEqualTo(1L)
        assertThat(questionSlot.captured.roomId).isEqualTo(roomId)
        assertThat(questionSlot.captured.targetMemberId).isEqualTo(targetMemberId)
        assertThat(questionSlot.captured.authorMemberId).isEqualTo(authorMemberId)
        assertThat(questionSlot.captured.parentQuestionId).isNull()
        assertThat(questionSlot.captured.source).isEqualTo(QuestionSource.PREPARATION)
    }

    @Test
    fun `같은 룸과 대상의 원 질문에 직접 꼬리질문을 기록한다`() {
        val parentQuestion = question(parentQuestionId = null)
        val followUpSlot = slot<QuestionEntity>()
        val savedFollowUp = mockk<QuestionEntity> { every { id } returns 2L }
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns parentQuestion
        every { questionRepository.save(capture(followUpSlot)) } returns savedFollowUp

        val followUpId = recorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            1L,
            "다시 선택한다면 같은 결정을 하시겠어요?",
            QuestionSource.PREPARATION,
        )

        assertThat(followUpId).isEqualTo(2L)
        assertThat(followUpSlot.captured.parentQuestionId).isEqualTo(1L)
        assertThat(followUpSlot.captured.targetMemberId).isEqualTo(targetMemberId)
    }

    @Test
    fun `부모가 다른 대상의 질문이면 꼬리질문을 기록하지 않고 E1507 을 던진다`() {
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns question(targetMemberId = UUID.randomUUID())

        assertRecordFails(CoreErrorType.QUESTION_NOT_FOUND) {
            recorder.record(
                roomId,
                targetMemberId,
                authorMemberId,
                1L,
                "꼬리질문",
                QuestionSource.PREPARATION,
            )
        }

        verify(exactly = 0) { questionRepository.save(any()) }
    }

    @Test
    fun `다른 룸의 부모 질문은 활성 잠금 조회에서 찾지 못해 E1507 을 던진다`() {
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns null

        assertRecordFails(CoreErrorType.QUESTION_NOT_FOUND) {
            recorder.record(
                roomId,
                targetMemberId,
                authorMemberId,
                1L,
                "꼬리질문",
                QuestionSource.PREPARATION,
            )
        }

        verify(exactly = 0) { questionRepository.save(any()) }
    }

    @Test
    fun `꼬리질문에는 다시 꼬리질문을 붙이지 않고 E1507 을 던진다`() {
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 2L)
        } returns question(parentQuestionId = 1L)

        assertRecordFails(CoreErrorType.QUESTION_NOT_FOUND) {
            recorder.record(
                roomId,
                targetMemberId,
                authorMemberId,
                2L,
                "다단계 꼬리질문",
                QuestionSource.PREPARATION,
            )
        }

        verify(exactly = 0) { questionRepository.save(any()) }
    }

    @Test
    fun `작성자는 자신의 활성 질문을 삭제한다`() {
        val question = question()
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns question
        every {
            questionRepository.existsByParentQuestionIdAndAuthorMemberIdNotAndDeletedAtIsNull(
                1L,
                authorMemberId,
            )
        } returns false
        every {
            questionRepository.findAllByParentQuestionIdAndAuthorMemberIdAndDeletedAtIsNull(
                1L,
                authorMemberId,
            )
        } returns emptyList()

        recorder.removeOwnedBy(roomId, 1L, authorMemberId, now)

        assertThat(question.isDeleted()).isTrue()
    }

    @Test
    fun `다른 사람이 질문을 삭제하면 상태를 유지하고 E1505 를 던진다`() {
        val question = question()
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns question

        assertRecordFails(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN) {
            recorder.removeOwnedBy(roomId, 1L, UUID.randomUUID(), now)
        }

        assertThat(question.isActive()).isTrue()
    }

    @Test
    fun `원 질문에 본인이 작성한 활성 꼬리질문만 있으면 모두 함께 소프트 삭제한다`() {
        val rootQuestion = question()
        val firstFollowUp = question(parentQuestionId = 1L)
        val secondFollowUp = question(parentQuestionId = 1L)
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns rootQuestion
        every {
            questionRepository.existsByParentQuestionIdAndAuthorMemberIdNotAndDeletedAtIsNull(
                1L,
                authorMemberId,
            )
        } returns false
        every {
            questionRepository.findAllByParentQuestionIdAndAuthorMemberIdAndDeletedAtIsNull(
                1L,
                authorMemberId,
            )
        } returns listOf(firstFollowUp, secondFollowUp)

        recorder.removeOwnedBy(roomId, 1L, authorMemberId, now)

        assertThat(rootQuestion.isDeleted()).isTrue()
        assertThat(firstFollowUp.isDeleted()).isTrue()
        assertThat(secondFollowUp.isDeleted()).isTrue()
    }

    @Test
    fun `꼬리질문을 직접 삭제하면 해당 질문만 소프트 삭제한다`() {
        val followUp = question(parentQuestionId = 1L)
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 2L)
        } returns followUp

        recorder.removeOwnedBy(roomId, 2L, authorMemberId, now)

        assertThat(followUp.isDeleted()).isTrue()
        verify(exactly = 0) {
            questionRepository.existsByParentQuestionIdAndAuthorMemberIdNotAndDeletedAtIsNull(any(), any())
        }
        verify(exactly = 0) {
            questionRepository.findAllByParentQuestionIdAndAuthorMemberIdAndDeletedAtIsNull(any(), any())
        }
    }

    @Test
    fun `원 질문에 다른 작성자의 활성 꼬리질문이 있으면 삭제하지 않고 E1508 을 던진다`() {
        val question = question()
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns question
        every {
            questionRepository.existsByParentQuestionIdAndAuthorMemberIdNotAndDeletedAtIsNull(
                1L,
                authorMemberId,
            )
        } returns true

        assertRecordFails(CoreErrorType.QUESTION_HAS_OTHER_FOLLOW_UP) {
            recorder.removeOwnedBy(roomId, 1L, authorMemberId, now)
        }

        assertThat(question.isActive()).isTrue()
        verify(exactly = 0) {
            questionRepository.findAllByParentQuestionIdAndAuthorMemberIdAndDeletedAtIsNull(any(), any())
        }
    }

    @Test
    fun `활성 질문을 찾을 수 없으면 E1507 을 던진다`() {
        every {
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, 1L)
        } returns null

        assertRecordFails(CoreErrorType.QUESTION_NOT_FOUND) {
            recorder.removeOwnedBy(roomId, 1L, authorMemberId, now)
        }
    }

    private fun question(
        roomId: UUID = this.roomId,
        targetMemberId: UUID = this.targetMemberId,
        authorMemberId: UUID = this.authorMemberId,
        parentQuestionId: Long? = null,
    ): QuestionEntity {
        return QuestionEntity(
            roomId = roomId,
            targetMemberId = targetMemberId,
            authorMemberId = authorMemberId,
            parentQuestionId = parentQuestionId,
            content = "질문",
            source = QuestionSource.PREPARATION,
        )
    }

    private fun assertRecordFails(errorType: CoreErrorType, block: () -> Unit) {
        assertThatThrownBy(block)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
