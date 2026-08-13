package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.storage.db.core.QuestionCommentEntity
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class QuestionCommentReader(
    private val targetValidator: QuestionCommentTargetValidator,
    private val questionCommentRepository: QuestionCommentRepository,
) {
    @Transactional(readOnly = true)
    fun getPage(
        roomId: UUID,
        targetMemberId: UUID,
        questionId: Long,
        cursor: QuestionCommentCursor?,
    ): QuestionCommentPage {
        targetValidator.validate(roomId, targetMemberId, questionId)
        val entities = questionCommentRepository.findPage(
            questionId = questionId,
            cursorCreatedAt = cursor?.createdAt,
            cursorId = cursor?.id,
            pageable = PageRequest.of(0, PAGE_FETCH_SIZE),
        )
        val comments = entities.take(PAGE_SIZE).map { it.toDomain() }
        val nextCursor = comments.lastOrNull()
            ?.takeIf { entities.size > PAGE_SIZE }
            ?.let { QuestionCommentCursor(it.createdAt, it.id) }
        return QuestionCommentPage(comments, nextCursor)
    }

    private fun QuestionCommentEntity.toDomain(): QuestionComment {
        return QuestionComment(
            id = id,
            questionId = questionId,
            authorMemberId = authorMemberId,
            type = commentType,
            content = content,
            createdAt = createdAt,
        )
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val PAGE_FETCH_SIZE = PAGE_SIZE + 1
    }
}
