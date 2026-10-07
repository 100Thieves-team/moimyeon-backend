package io.plady.moimyeon.core.domain.question

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.QuestionSource
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

private val log = KotlinLogging.logger {}

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
        log.debug { "question.preparation.leave memberId=$authorMemberId roomId=$roomId targetMemberId=$targetMemberId" }
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
        parentQuestionId: Long,
        content: String,
    ): Long {
        log.debug { "question.preparation.follow-up.leave memberId=$authorMemberId roomId=$roomId parentQuestionId=$parentQuestionId" }
        accessValidator.validateAuthor(roomId, authorMemberId)
        return questionRecorder.recordFollowUp(
            roomId,
            authorMemberId,
            parentQuestionId,
            content,
            QuestionSource.PREPARATION,
        )
    }

    fun deleteQuestion(authorMemberId: UUID, roomId: UUID, questionId: Long) {
        log.debug { "question.preparation.delete memberId=$authorMemberId roomId=$roomId questionId=$questionId" }
        accessValidator.validateAuthor(roomId, authorMemberId)
        questionRecorder.removeOwnedBy(roomId, questionId, authorMemberId, now())
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
