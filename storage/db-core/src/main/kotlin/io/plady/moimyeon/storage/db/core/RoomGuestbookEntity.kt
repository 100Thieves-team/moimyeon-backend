package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(
    name = "room_guestbook",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_room_guestbook_room_active",
            columnNames = ["room_id", "_active_check"],
        ),
    ],
)
class RoomGuestbookEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
) : BaseEntity()
