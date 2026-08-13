package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.QuestionSource
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "question")
class QuestionEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val targetMemberId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val authorMemberId: UUID,
    val parentQuestionId: Long? = null,
    val content: String,
    @Enumerated(EnumType.STRING)
    val source: QuestionSource,
    asked: Boolean = false,
) : BaseEntity() {
    var asked: Boolean = asked
        protected set

    fun markAsked() {
        asked = true
    }
}
