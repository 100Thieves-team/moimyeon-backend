package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(
    name = "answer_summary",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_answer_summary_question_author_active",
            columnNames = ["question_id", "author_member_id", "_active_check"],
        ),
    ],
)
class AnswerSummaryEntity(
    val questionId: Long,
    @JdbcTypeCode(SqlTypes.BINARY)
    val authorMemberId: UUID,
    val content: String,
) : BaseEntity()
