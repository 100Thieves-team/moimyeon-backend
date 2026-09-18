package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoomStatusLogHandlerType
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
class RoomStatusLogEntity private constructor(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @Enumerated(EnumType.STRING)
    val transitionType: RoomStatus,
    @Enumerated(EnumType.STRING)
    val handlerType: RoomStatusLogHandlerType,
    @JdbcTypeCode(SqlTypes.BINARY)
    val handlerMemberId: UUID?,
    val occurredAt: LocalDateTime,
) : BaseEntity() {
    companion object {
        // 주체 불변식(MEMBER ⇒ 회원 id, SYSTEM ⇒ NULL)은 이 팩토리 둘이 지킨다.
        // 생성자를 열면 SYSTEM + 아무 id 조합이 컴파일을 통과한다.
        fun byMember(
            roomId: UUID,
            transitionType: RoomStatus,
            handlerMemberId: UUID,
            occurredAt: LocalDateTime,
        ): RoomStatusLogEntity = RoomStatusLogEntity(
            roomId = roomId,
            transitionType = transitionType,
            handlerType = RoomStatusLogHandlerType.MEMBER,
            handlerMemberId = handlerMemberId,
            occurredAt = occurredAt,
        )

        fun bySystem(
            roomId: UUID,
            transitionType: RoomStatus,
            occurredAt: LocalDateTime,
        ): RoomStatusLogEntity = RoomStatusLogEntity(
            roomId = roomId,
            transitionType = transitionType,
            handlerType = RoomStatusLogHandlerType.SYSTEM,
            handlerMemberId = null,
            occurredAt = occurredAt,
        )
    }
}
