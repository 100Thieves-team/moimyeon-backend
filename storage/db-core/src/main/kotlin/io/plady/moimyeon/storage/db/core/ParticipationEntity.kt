package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

// 신청 수락으로 룸 명단에 들어온 참여. 방장은 role=HOST row 가 유일한 진실 원천이다(룸에 host FK 없음).
// 룸 확정 전 취소·내보내기도 이력으로 남으므로, 확정 참여자 명단은 room_status_log 의 확정 시각과
// joinedAt/leftAt 구간을 비교해 판정한다.
@Entity
@Table(
    name = "participation",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_participation_room_member_active",
            columnNames = ["room_id", "member_id", "_active_check"],
        ),
    ],
)
class ParticipationEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    @Enumerated(EnumType.STRING)
    val participationRole: ParticipationRole,
    @Enumerated(EnumType.STRING)
    val status: ParticipationStatus,
    val joinedAt: LocalDateTime,
    @JdbcTypeCode(SqlTypes.BINARY)
    val leftByMemberId: UUID? = null,
    val leftAt: LocalDateTime? = null,
) : BaseEntity()
