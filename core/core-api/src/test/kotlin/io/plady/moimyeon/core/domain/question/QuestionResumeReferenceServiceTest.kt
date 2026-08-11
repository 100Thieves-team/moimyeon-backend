package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestionResumeReferenceServiceTest {
    private val accessValidator = mockk<QuestionCardSetAccessValidator>()
    private val referenceReader = mockk<QuestionResumeReferenceReader>()
    private val service = QuestionResumeReferenceService(accessValidator, referenceReader)

    private val roomId = UUID.randomUUID()
    private val requesterMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()

    @Test
    fun `확정 참여자는 대상이 이 룸에 제출한 이력서의 완료된 AI 요약을 조회한다`() {
        val reference = reference(
            status = ResumeSummaryStatus.DONE,
            content = "결제 도메인 경험이 있는 백엔드 개발자",
        )
        givenViewerCanRead(reference)

        val result = service.getResumeReference(requesterMemberId, roomId, targetMemberId)

        assertThat(result).isEqualTo(reference)
        verifyOrder {
            accessValidator.validateViewer(roomId, requesterMemberId)
            accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
            referenceReader.getByRoomAndTarget(roomId, targetMemberId)
        }
    }

    @Test
    fun `확정 참여자 열람 검증에 실패하면 대상의 제출 이력서를 읽지 않는다`() {
        every {
            accessValidator.validateViewer(roomId, requesterMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)

        assertThatThrownBy {
            service.getResumeReference(requesterMemberId, roomId, targetMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)
        }

        verify(exactly = 0) { accessValidator.validateOtherCardSetTarget(any(), any(), any()) }
        verify(exactly = 0) { referenceReader.getByRoomAndTarget(any(), any()) }
    }

    @Test
    fun `본인 요약을 요청하면 참고 자료를 읽지 않고 QUESTION_CARD_SET_FORBIDDEN 을 던진다`() {
        justRun { accessValidator.validateViewer(roomId, requesterMemberId) }
        every {
            accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, requesterMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)

        assertThatThrownBy {
            service.getResumeReference(requesterMemberId, roomId, requesterMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)
        }

        verify(exactly = 0) { referenceReader.getByRoomAndTarget(any(), any()) }
    }

    @Test
    fun `대상이 확정 시점 참여자가 아니면 참고 자료를 읽지 않고 QUESTION_CARD_SET_NOT_FOUND 를 던진다`() {
        justRun { accessValidator.validateViewer(roomId, requesterMemberId) }
        every {
            accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_NOT_FOUND)

        assertThatThrownBy {
            service.getResumeReference(requesterMemberId, roomId, targetMemberId)
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.QUESTION_CARD_SET_NOT_FOUND)
        }

        verify(exactly = 0) { referenceReader.getByRoomAndTarget(any(), any()) }
    }

    private fun givenViewerCanRead(reference: QuestionResumeReference) {
        justRun { accessValidator.validateViewer(roomId, requesterMemberId) }
        justRun {
            accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
        }
        every { referenceReader.getByRoomAndTarget(roomId, targetMemberId) } returns reference
    }

    private fun reference(
        status: ResumeSummaryStatus,
        content: String?,
    ): QuestionResumeReference {
        check(status == ResumeSummaryStatus.DONE)
        return QuestionResumeReference(
            targetMemberId = targetMemberId,
            summary = QuestionResumeSummary.Done(checkNotNull(content)),
        )
    }
}
