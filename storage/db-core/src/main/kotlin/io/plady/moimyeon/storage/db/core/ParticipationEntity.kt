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

// 룸 참여. 방장은 role=HOST row 가 유일한 진실 원천이다(룸에 host FK 없음).
// 대기/신청(room_application)과 달리 이 테이블은 "확정 참여자"만 담는다.
// 생성 이후의 참여 상태 전이(퇴장 등)는 이 스프린트 밖이라, 지금은 방장 등록(HOST/JOINED)만 쓴다.
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
