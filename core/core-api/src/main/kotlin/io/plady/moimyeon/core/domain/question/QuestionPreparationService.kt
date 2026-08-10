package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.core.enums.QuestionSource
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class QuestionPreparationService(
    private val accessValidator: QuestionPreparationAccessValidator,
    private val questionRecorder: QuestionRecorder,
    private val clock: Clock,
) {
    fun leaveQuestion(
        authorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        content: String,
    ): Long {
        accessValidator.validateAuthor(roomId, authorMemberId)
        accessValidator.validateTarget(roomId, authorMemberId, targetMemberId)
        return questionRecorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            null,
            content,
            QuestionSource.PREPARATION,
        )
    }

    fun leaveFollowUp(
        authorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        parentQuestionId: Long,
        content: String,
    ): Long {
        accessValidator.validateAuthor(roomId, authorMemberId)
        accessValidator.validateTarget(roomId, authorMemberId, targetMemberId)
        return questionRecorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            parentQuestionId,
            content,
            QuestionSource.PREPARATION,
        )
    }

    fun deleteQuestion(authorMemberId: UUID, roomId: UUID, questionId: Long) {
        accessValidator.validateAuthor(roomId, authorMemberId)
        questionRecorder.removeOwnedBy(roomId, questionId, authorMemberId, now())
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
