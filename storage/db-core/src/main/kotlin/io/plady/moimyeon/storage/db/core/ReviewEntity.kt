package io.plady.moimyeon.storage.db.core

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "review",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_review_room_author_target_active",
            columnNames = ["room_id", "author_member_id", "target_member_id", "_active_check"],
        ),
    ],
)
class ReviewEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val authorMemberId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val targetMemberId: UUID,
    val rating: Short,
    val content: String? = null,
    val meetAgain: Boolean? = null,
    val visibleAt: LocalDateTime,
    val hiddenAt: LocalDateTime? = null,
    val reportedAt: LocalDateTime? = null,
    tags: Collection<String> = emptyList(),
) : BaseEntity() {
    @ElementCollection
    @CollectionTable(name = "review_tag", joinColumns = [JoinColumn(name = "review_id")])
    @Column(name = "tag")
    private val tags: MutableSet<String> = tags.toMutableSet()

    fun tags(): Set<String> = tags.toSet()
}
