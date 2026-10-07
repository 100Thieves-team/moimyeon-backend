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
            name = "uk_participation_room_member_joined",
            columnNames = ["room_id", "member_id", "_joined_check"],
        ),
    ],
)
class ParticipationEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    participationRole: ParticipationRole,
    status: ParticipationStatus,
    val joinedAt: LocalDateTime,
    leftByMemberId: UUID? = null,
    leftAt: LocalDateTime? = null,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    var participationRole: ParticipationRole = participationRole
        protected set

    @Enumerated(EnumType.STRING)
    var status: ParticipationStatus = status
        protected set

    @JdbcTypeCode(SqlTypes.BINARY)
    var leftByMemberId: UUID? = leftByMemberId
        protected set

    var leftAt: LocalDateTime? = leftAt
        protected set

    // 셋을 함께 쓴다. status 만 바꾸고 leftAt 을 빠뜨리면 확정 후 이탈 판정
    // (left_at > confirmed_at)이 조용히 틀어진다.
    // leftBy 는 자진 이탈(본인)과 내보내기(방장)를 가른다 — 재신청 차단이 이 값으로 판정한다.
    fun leave(now: LocalDateTime, leftBy: UUID) {
        check(status == ParticipationStatus.JOINED)
        status = ParticipationStatus.LEFT
        leftAt = now
        leftByMemberId = leftBy
    }

    // 방장이 나갈 때의 자동 위임. 전 방장의 role 은 HOST 로 남으므로(MOI-397) 이 시점에
    // 한 룸에 HOST 행이 둘이 되고, 현재 방장은 status 로 갈린다.
    fun promoteToHost() {
        check(status == ParticipationStatus.JOINED)
        participationRole = ParticipationRole.HOST
    }
}
