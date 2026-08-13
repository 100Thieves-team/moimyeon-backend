package io.plady.moimyeon.core.domain.round

import io.plady.moimyeon.core.domain.progress.RoomProgressAccessValidator
import io.plady.moimyeon.core.domain.question.QuestionCardSetAccessValidator
import io.plady.moimyeon.core.domain.question.QuestionCardSetReader
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RoundService(
    private val progressAccessValidator: RoomProgressAccessValidator,
    private val questionCardSetAccessValidator: QuestionCardSetAccessValidator,
    private val questionCardSetReader: QuestionCardSetReader,
) {
    fun getScreen(
        memberId: UUID,
        roomId: UUID,
        intervieweeMemberId: UUID,
    ): RoundScreen {
        progressAccessValidator.validateRailViewer(roomId, memberId)
        if (memberId == intervieweeMemberId) {
            return RoundScreen.Interviewee(intervieweeMemberId)
        }

        questionCardSetAccessValidator.validateOtherCardSetTarget(
            roomId = roomId,
            requesterMemberId = memberId,
            targetMemberId = intervieweeMemberId,
        )
        return RoundScreen.Participant(
            intervieweeMemberId = intervieweeMemberId,
            questionCardSet = questionCardSetReader.getByRoomAndTarget(roomId, intervieweeMemberId),
        )
    }
}
