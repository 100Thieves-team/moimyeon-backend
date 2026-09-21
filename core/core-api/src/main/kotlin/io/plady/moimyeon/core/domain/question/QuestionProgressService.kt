package io.plady.moimyeon.core.domain.question

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.progress.RoomProgressAccessValidator
import io.plady.moimyeon.core.enums.QuestionSource
import org.springframework.stereotype.Service
import java.util.UUID

private val log = KotlinLogging.logger {}

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
        log.debug { "question.progress.leave memberId=$actorMemberId roomId=$roomId targetMemberId=$targetMemberId" }
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
        log.debug { "question.progress.follow-up.leave memberId=$actorMemberId roomId=$roomId parentQuestionId=$parentQuestionId" }
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
        log.debug { "question.progress.asked.change memberId=$actorMemberId roomId=$roomId questionId=$questionId asked=$asked" }
        progressAccessValidator.validateInProgressParticipant(roomId, actorMemberId)
        cardSetAccessValidator.validateOtherCardSetTarget(roomId, actorMemberId, targetMemberId)
        questionUsageMarker.changeAsked(roomId, targetMemberId, questionId, asked)
    }
}
