package io.plady.moimyeon.core.domain.question

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.enums.QuestionCommentType
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class QuestionCommentService(
    private val accessValidator: QuestionCommentAccessValidator,
    private val commentManager: QuestionCommentManager,
    private val commentReader: QuestionCommentReader,
    private val clock: Clock,
) {
    fun leaveComment(
        actorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        type: QuestionCommentType,
        content: String,
    ): Long {
        log.debug { "question.comment.leave memberId=$actorMemberId roomId=$roomId targetMemberId=$targetMemberId questionId=$questionId type=$type" }
        accessValidator.validateWriter(roomId, actorMemberId, targetMemberId)
        return commentManager.record(roomId, targetMemberId, questionId, actorMemberId, type, content)
    }

    fun toggleType(
        actorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        commentId: Long,
        type: QuestionCommentType,
    ) {
        log.debug { "question.comment.type.toggle memberId=$actorMemberId roomId=$roomId questionId=$questionId commentId=$commentId type=$type" }
        accessValidator.validateWriter(roomId, actorMemberId, targetMemberId)
        commentManager.toggleType(roomId, targetMemberId, questionId, commentId, actorMemberId, type)
    }

    fun editComment(
        actorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        commentId: Long,
        content: String,
    ) {
        log.debug { "question.comment.edit memberId=$actorMemberId roomId=$roomId questionId=$questionId commentId=$commentId" }
        accessValidator.validateWriter(roomId, actorMemberId, targetMemberId)
        commentManager.edit(roomId, targetMemberId, questionId, commentId, actorMemberId, content)
    }

    fun deleteComment(
        actorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        commentId: Long,
    ) {
        log.debug { "question.comment.delete memberId=$actorMemberId roomId=$roomId questionId=$questionId commentId=$commentId" }
        accessValidator.validateWriter(roomId, actorMemberId, targetMemberId)
        commentManager.remove(roomId, targetMemberId, questionId, commentId, actorMemberId, now())
    }

    fun getComments(
        actorMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        cursor: QuestionCommentCursor? = null,
    ): QuestionCommentPage {
        accessValidator.validateViewer(roomId, actorMemberId, targetMemberId)
        return commentReader.getPage(roomId, targetMemberId, questionId, cursor)
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS)
}
