package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
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
    val commentType: String,
    val content: String,
) : BaseEntity()
