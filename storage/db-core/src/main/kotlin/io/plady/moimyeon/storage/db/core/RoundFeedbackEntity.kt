package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoundFeedbackType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "round_feedback",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_round_feedback_round_author_active",
            columnNames = ["room_id", "interviewee_member_id", "author_member_id", "_active_check"],
        ),
    ],
)
class RoundFeedbackEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val intervieweeMemberId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val authorMemberId: UUID,
    @Enumerated(EnumType.STRING)
    val feedbackType: RoundFeedbackType,
    content: String,
) : BaseEntity() {
    var content: String = content
        protected set

    var disclosedAt: LocalDateTime? = null
        protected set

    fun edit(content: String) {
        check(feedbackType == RoundFeedbackType.SELF) { "자가 피드백만 수정할 수 있습니다." }
        this.content = content
    }

    fun disclose(at: LocalDateTime) {
        check(feedbackType == RoundFeedbackType.FINAL) { "최종 피드백만 열람 확인할 수 있습니다." }
        if (disclosedAt == null) disclosedAt = at
    }
}
