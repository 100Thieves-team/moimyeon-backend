package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.QuestionCommentType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "question_comment")
class QuestionCommentEntity(
    val questionId: Long,
    @JdbcTypeCode(SqlTypes.BINARY)
    val authorMemberId: UUID,
    commentType: QuestionCommentType,
    content: String,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    var commentType: QuestionCommentType = commentType
        protected set

    var content: String = content
        protected set

    fun toggleType(clickedType: QuestionCommentType) {
        commentType = if (commentType == clickedType) QuestionCommentType.MEMO else clickedType
    }

    fun edit(content: String) {
        this.content = content
    }
}
