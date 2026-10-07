package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "review_skip",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_review_skip_room_author_target",
            columnNames = ["room_id", "author_member_id", "target_member_id"],
        ),
    ],
)
class ReviewSkipEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val authorMemberId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val targetMemberId: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN
}
