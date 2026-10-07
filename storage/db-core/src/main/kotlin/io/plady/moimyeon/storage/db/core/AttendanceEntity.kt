package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.AttendanceStatus
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
    name = "attendance",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_attendance_room_member_active",
            columnNames = ["room_id", "member_id", "_active_check"],
        ),
    ],
)
class AttendanceEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    @Enumerated(EnumType.STRING)
    val status: AttendanceStatus,
    val changeReason: String? = null,
    @JdbcTypeCode(SqlTypes.BINARY)
    val recorderMemberId: UUID,
    val recordedAt: LocalDateTime,
) : BaseEntity()
