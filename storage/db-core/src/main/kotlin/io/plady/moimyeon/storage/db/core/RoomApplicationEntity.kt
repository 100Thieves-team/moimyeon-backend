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

    companion object {
        // 방장 제출(MOI-333). 방장은 신청을 거치지 않지만 제출은 신청 행을 참조하므로
        // 룸 생성과 함께 이미 처리된 신청 행을 만든다. 대기를 거치지 않아 pendingMemberId 는 null 이고,
        // 그래서 대기 수·개인 대기 한도·대기 유니크 어디에도 잡히지 않는다.
        // note 는 NOT NULL 이고 방장에게는 전달할 말이 없어 빈 문자열이다.
        fun forHost(roomId: UUID, hostMemberId: UUID, at: LocalDateTime): RoomApplicationEntity {
            val application = RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = hostMemberId,
                note = "",
                appliedAt = at,
                status = RoomApplicationStatus.ACCEPTED,
                pendingMemberId = null,
            )
            application.handlerMemberId = hostMemberId
            application.handledAt = at
            return application
        }
    }

    // 수락: 참여자 등록은 호출부(RoomApplicationManager)가 별도 participation 으로 처리하고,
    // 여기서는 신청 자신의 상태 전이만 책임진다.
    fun accept(handlerMemberId: UUID, now: LocalDateTime) {
        this.status = RoomApplicationStatus.ACCEPTED
        this.pendingMemberId = null
        this.handlerMemberId = handlerMemberId
        this.handledAt = now
    }

    fun reject(handlerMemberId: UUID, reason: String?, now: LocalDateTime) {
        this.status = RoomApplicationStatus.REJECTED
        this.pendingMemberId = null
        this.rejectReason = reason
        this.handlerMemberId = handlerMemberId
        this.handledAt = now
    }

    // 신청자의 참여 슬롯이 차서 시스템이 끝낸다(MOI-427). 방장이 수락을 눌러 촉발되지만 방장의 판단이
    // 아니므로 handlerMemberId 를 채우지 않는다 — closeAllPending(룸 취소·확정)과 같은 계열이다.
    fun closeBySlotExceeded(now: LocalDateTime) {
        this.status = RoomApplicationStatus.SLOT_EXCEEDED
        this.pendingMemberId = null
        this.handledAt = now
    }

    fun withdraw(now: LocalDateTime) {
        this.status = RoomApplicationStatus.WITHDRAWN
        this.pendingMemberId = null
        this.handledAt = now
    }
}
