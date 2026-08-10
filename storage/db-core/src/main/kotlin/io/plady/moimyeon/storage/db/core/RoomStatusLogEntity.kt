package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomStatus
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
    name = "room_status_log",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_room_status_log_room_transition_active",
            columnNames = ["room_id", "transition_type", "_active_check"],
        ),
    ],
)
class RoomStatusLogEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @Enumerated(EnumType.STRING)
    val transitionType: RoomStatus,
    @JdbcTypeCode(SqlTypes.BINARY)
    val handlerMemberId: UUID,
    val occurredAt: LocalDateTime,
) : BaseEntity()
