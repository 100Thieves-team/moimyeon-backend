package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "resume_submission",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_resume_submission_room_member_active",
            columnNames = ["room_id", "member_id", "_active_check"],
        ),
    ],
)
class ResumeSubmissionEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val sourceResumeId: UUID,
    val fileKey: String,
    val originalName: String,
    val sizeBytes: Long,
    val contentType: String,
    val submittedAt: LocalDateTime,
) : BaseEntity()
