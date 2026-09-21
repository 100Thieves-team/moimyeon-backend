package io.plady.moimyeon.core.domain.question

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class QuestionMemoRecordReader(
    private val questionRepository: QuestionRepository,
    private val questionCommentRepository: QuestionCommentRepository,
) {
    @Transactional(readOnly = true)
    fun getAskedRecordsByAuthor(
        roomId: UUID,
        intervieweeMemberId: UUID,
        authorMemberId: UUID,
    ): List<QuestionMemoRecord> {
        log.debug { "question-memo-record.reader.getAskedRecordsByAuthor roomId=$roomId intervieweeMemberId=$intervieweeMemberId authorMemberId=$authorMemberId" }
        val questions = questionRepository
            .findByRoomIdAndTargetMemberIdAndAskedTrueAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                intervieweeMemberId,
            )
        if (questions.isEmpty()) return emptyList()

        val commentsByQuestionId = questionCommentRepository
            .findAllByQuestionIdInAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                questions.map { it.id },
                authorMemberId,
            )
            .groupBy { it.questionId }
        return questions.mapNotNull { question ->
            val comments = commentsByQuestionId[question.id].orEmpty()
            if (comments.isEmpty()) return@mapNotNull null
            QuestionMemoRecord(
                questionId = question.id,
                questionContent = question.content,
                comments = comments.map { comment ->
                    QuestionMemoComment(
                        id = comment.id,
                        type = comment.commentType,
                        content = comment.content,
                        createdAt = comment.createdAt,
                    )
                },
            )
        }
    }
}
