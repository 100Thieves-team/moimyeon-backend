package io.plady.moimyeon.storage.db.core

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(
    name = "closing_response",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_closing_response_room_member_active",
            columnNames = ["room_id", "member_id", "_active_check"],
        ),
    ],
)
class ClosingResponseEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    questionVotes: List<QuestionVoteEntity>,
) : BaseEntity() {
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "closing_response_id", nullable = false)
    private val questionVotes: MutableList<QuestionVoteEntity> = questionVotes.toMutableList()

    fun questionVotes(): List<QuestionVoteEntity> = questionVotes.toList()
}
