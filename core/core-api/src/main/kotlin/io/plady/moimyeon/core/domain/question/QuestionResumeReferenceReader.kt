package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.domain.resume.ResumeSummary
import io.plady.moimyeon.core.domain.room.RoomParticipantResumeFinder
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class QuestionResumeReferenceReader(
    private val roomParticipantResumeFinder: RoomParticipantResumeFinder,
) {
    fun getByRoomAndTarget(roomId: UUID, targetMemberId: UUID): QuestionResumeReference {
        val participantResume = roomParticipantResumeFinder.get(roomId, targetMemberId)
        return QuestionResumeReference(
            targetMemberId = targetMemberId,
            summary = participantResume.summary.toQuestionResumeSummary(),
        )
    }

    private fun ResumeSummary.toQuestionResumeSummary(): QuestionResumeSummary {
        return when (status) {
            ResumeSummaryStatus.DONE -> QuestionResumeSummary.Done(
                checkNotNull(content) { "완료된 이력서 요약에는 내용이 있어야 합니다" },
            )
            ResumeSummaryStatus.PROCESSING -> QuestionResumeSummary.Processing
            ResumeSummaryStatus.FAILED -> QuestionResumeSummary.Failed
        }
    }
}
