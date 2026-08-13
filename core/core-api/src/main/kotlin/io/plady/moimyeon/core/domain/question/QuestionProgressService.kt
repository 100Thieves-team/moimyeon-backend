package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.domain.progress.RoomProgressAccessValidator
import io.plady.moimyeon.core.enums.QuestionSource
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class QuestionProgressService(
    private val progressAccessValidator: RoomProgressAccessValidator,
    private val cardSetAccessValidator: QuestionCardSetAccessValidator,
    private val questionUsageMarker: QuestionUsageMarker,
    private val questionRecorder: QuestionRecorder,
) {
    fun leaveQuestion(
        actorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        content: String,
    ): Long {
        progressAccessValidator.validateInProgressParticipant(roomId, actorMemberId)
        cardSetAccessValidator.validateOtherCardSetTarget(roomId, actorMemberId, targetMemberId)
        return questionRecorder.record(
            roomId,
            targetMemberId,
            actorMemberId,
            null,
            content,
            QuestionSource.IN_PROGRESS,
        )
    }

    fun leaveFollowUp(
        actorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        parentQuestionId: Long,
        content: String,
    ): Long {
        progressAccessValidator.validateInProgressParticipant(roomId, actorMemberId)
        cardSetAccessValidator.validateOtherCardSetTarget(roomId, actorMemberId, targetMemberId)
        return questionRecorder.record(
            roomId,
            targetMemberId,
            actorMemberId,
            parentQuestionId,
            content,
            QuestionSource.IN_PROGRESS,
        )
    }

    fun changeAsked(
        actorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        asked: Boolean,
    ) {
        progressAccessValidator.validateInProgressParticipant(roomId, actorMemberId)
        cardSetAccessValidator.validateOtherCardSetTarget(roomId, actorMemberId, targetMemberId)
        questionUsageMarker.changeAsked(roomId, targetMemberId, questionId, asked)
    }
}
