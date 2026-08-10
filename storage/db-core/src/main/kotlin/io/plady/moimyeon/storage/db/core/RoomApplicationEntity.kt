package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

// 참가 신청. 신청 제출은 별도 이슈이며, 이 스프린트(MOI-328)에서는 방장의 수락·반려만 다룬다.
// pending_member_id: 상태가 대기(PENDING)면 신청자 id 를, 처리되면 NULL 을 애플리케이션이 채운다.
//   uk (room_id, pending_member_id, _active_check) 가 "룸당 회원당 대기 신청 1건"을 보장한다(schema.sql 주석).
//   처리(수락·반려·철회)되면 NULL 로 풀어 재신청 여지를 남긴다.
@Entity
@Table(
    name = "room_application",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_room_application_room_pending_active",
            columnNames = ["room_id", "pending_member_id", "_active_check"],
        ),
    ],
)
class RoomApplicationEntity(
    @JdbcTypeCode(SqlTypes.BINARY)
    val roomId: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val applicantMemberId: UUID,
    val note: String,
    val appliedAt: LocalDateTime,
    status: RoomApplicationStatus,
    pendingMemberId: UUID?,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    var status: RoomApplicationStatus = status
        protected set

    // 대기 유니크 표현 컬럼. 대기면 신청자 id, 처리되면 NULL.
    @JdbcTypeCode(SqlTypes.BINARY)
    var pendingMemberId: UUID? = pendingMemberId
        protected set

    var rejectReason: String? = null
        protected set

    @JdbcTypeCode(SqlTypes.BINARY)
    var handlerMemberId: UUID? = null
        protected set

    var handledAt: LocalDateTime? = null
        protected set

    fun isPending(): Boolean = status == RoomApplicationStatus.PENDING

    // 수락: 참여자 등록은 호출부(RoomApplicationManager)가 별도 participation 으로 처리하고,
    // 여기서는 신청 자신의 상태 전이만 책임진다. 대기에서 빠지므로 pendingMemberId 를 비운다.
    fun accept(handlerMemberId: UUID, now: LocalDateTime) {
        this.status = RoomApplicationStatus.ACCEPTED
        this.pendingMemberId = null
        this.handlerMemberId = handlerMemberId
        this.handledAt = now
    }

    // 반려: 사유는 선택(§4.4). 정원·참여자에는 영향이 없다.
    fun reject(handlerMemberId: UUID, reason: String?, now: LocalDateTime) {
        this.status = RoomApplicationStatus.REJECTED
        this.pendingMemberId = null
        this.rejectReason = reason
        this.handlerMemberId = handlerMemberId
        this.handledAt = now
    }

    // 철회는 신청의 결말이다. 제출 당시 기록은 보존하고 대기 유니크 자리만 해제한다.
    fun withdraw(now: LocalDateTime) {
        this.status = RoomApplicationStatus.WITHDRAWN
        this.pendingMemberId = null
        this.handledAt = now
    }
}
