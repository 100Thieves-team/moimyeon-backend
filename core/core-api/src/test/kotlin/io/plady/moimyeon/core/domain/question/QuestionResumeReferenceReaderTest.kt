package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.resume.ResumeSummary
import io.plady.moimyeon.core.domain.room.RoomParticipantResume
import io.plady.moimyeon.core.domain.room.RoomParticipantResumeFinder
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestionResumeReferenceReaderTest {
    private val roomParticipantResumeFinder = mockk<RoomParticipantResumeFinder>()
    private val reader = QuestionResumeReferenceReader(roomParticipantResumeFinder)

    private val roomId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val submittedResumeId = UUID.randomUUID()

    @Test
    fun `룸 참여자가 제출한 이력서 참조를 따라 완료된 AI 요약을 조립한다`() {
        val submittedResume = roomParticipantResume(
            status = ResumeSummaryStatus.DONE,
            content = "결제 도메인 경험이 있는 백엔드 개발자",
        )
        every {
            roomParticipantResumeFinder.get(roomId, targetMemberId)
        } returns submittedResume

        val result = reader.getByRoomAndTarget(roomId, targetMemberId)

        assertThat(result).isEqualTo(
            QuestionResumeReference(
                targetMemberId = targetMemberId,
                summary = QuestionResumeSummary.Done("결제 도메인 경험이 있는 백엔드 개발자"),
            ),
        )
        verify(exactly = 1) { roomParticipantResumeFinder.get(roomId, targetMemberId) }
    }

    @Test
    fun `회원의 현재 기본 이력서가 아니라 룸에 제출한 이력서의 AI 요약을 조회한다`() {
        every {
            roomParticipantResumeFinder.get(roomId, targetMemberId)
        } returns roomParticipantResume(
            status = ResumeSummaryStatus.DONE,
            content = "제출 당시 선택한 이력서 요약",
        )

        val result = reader.getByRoomAndTarget(roomId, targetMemberId)

        assertThat(result.summary).isEqualTo(
            QuestionResumeSummary.Done("제출 당시 선택한 이력서 요약"),
        )
    }

    @Test
    fun `제출 이력서 요약이 생성 중이면 내용 없이 PROCESSING 상태를 보존한다`() {
        val result = readReference(ResumeSummaryStatus.PROCESSING, null)

        assertThat(result.summary).isEqualTo(QuestionResumeSummary.Processing)
    }

    @Test
    fun `제출 이력서 요약 생성에 실패하면 PROCESSING과 구분되는 FAILED 상태를 보존한다`() {
        val result = readReference(ResumeSummaryStatus.FAILED, null)

        assertThat(result.summary).isEqualTo(QuestionResumeSummary.Failed)
    }

    @Test
    fun `완료된 제출 이력서 요약의 내용이 없으면 불변식 위반으로 실패한다`() {
        every {
            roomParticipantResumeFinder.get(roomId, targetMemberId)
        } returns roomParticipantResume(ResumeSummaryStatus.DONE, null)

        assertThatThrownBy {
            reader.getByRoomAndTarget(roomId, targetMemberId)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("완료된 이력서 요약")
    }

    @Test
    fun `확정 참여자의 룸 제출 이력서 참조가 없으면 저장 불변식 위반으로 실패한다`() {
        every {
            roomParticipantResumeFinder.get(roomId, targetMemberId)
        } throws IllegalStateException("확정 참여자에게는 룸 제출 이력서 참조가 있어야 합니다")

        assertThatThrownBy {
            reader.getByRoomAndTarget(roomId, targetMemberId)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("룸 제출 이력서 참조")

        verify(exactly = 1) { roomParticipantResumeFinder.get(roomId, targetMemberId) }
    }

    private fun readReference(
        status: ResumeSummaryStatus,
        content: String?,
    ): QuestionResumeReference {
        every {
            roomParticipantResumeFinder.get(roomId, targetMemberId)
        } returns roomParticipantResume(status, content)
        return reader.getByRoomAndTarget(roomId, targetMemberId)
    }

    private fun roomParticipantResume(
        status: ResumeSummaryStatus,
        content: String?,
    ): RoomParticipantResume {
        return RoomParticipantResume(
            roomId = roomId,
            participantMemberId = targetMemberId,
            submissionId = 1L,
            sourceResumeId = submittedResumeId,
            summary = ResumeSummary(status, content),
        )
    }
}
