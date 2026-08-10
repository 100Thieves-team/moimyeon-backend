package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoomApplicationRepositoryIT(
    private val roomApplicationRepository: RoomApplicationRepository,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)

    @Test
    fun `룸별 대기 중인 신청 수를 한 번에 집계한다`() {
        val busy = UUID.randomUUID()
        val quiet = UUID.randomUUID()
        repeat(3) { apply(busy, RoomApplicationStatus.PENDING) }
        apply(quiet, RoomApplicationStatus.PENDING)

        val counts = roomApplicationRepository.countPendingByRoomIds(listOf(busy, quiet)).associate { it.roomId to it.count }

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(mapOf(busy to 3L, quiet to 1L))
    }

    @Test
    fun `철회·반려·수락된 신청은 대기 수에서 제외한다`() {
        val roomId = UUID.randomUUID()
        apply(roomId, RoomApplicationStatus.PENDING)
        apply(roomId, RoomApplicationStatus.WITHDRAWN)
        apply(roomId, RoomApplicationStatus.REJECTED)
        apply(roomId, RoomApplicationStatus.ACCEPTED)

        assertThat(roomApplicationRepository.countPendingByRoomIds(listOf(roomId)).single().count).isEqualTo(1)
    }

    @Test
    fun `삭제된 신청은 대기 수에서 제외한다`() {
        val roomId = UUID.randomUUID()
        apply(roomId, RoomApplicationStatus.PENDING)
        apply(roomId, RoomApplicationStatus.PENDING).also { it.delete(now) }
        roomApplicationRepository.flush()

        assertThat(roomApplicationRepository.countPendingByRoomIds(listOf(roomId)).single().count).isEqualTo(1)
    }

    // 이 계약을 호출자가 알아야 한다. 0 을 채우지 않으면 응답 조립에서 키가 없어 터진다.
    @Test
    fun `대기 신청이 없는 룸은 결과에 아예 나오지 않는다`() {
        val roomId = UUID.randomUUID()
        apply(roomId, RoomApplicationStatus.ACCEPTED)

        assertThat(roomApplicationRepository.countPendingByRoomIds(listOf(roomId))).isEmpty()
    }

    // 지우면 몇 명이 기다리고 있었는지가 사라진다(MOI-394). 목록에서 빼는 것은 철회뿐이다.
    @Test
    fun `룸 취소나 확정으로 끝난 신청도 방장 목록에 남는다`() {
        val roomId = UUID.randomUUID()
        apply(roomId, RoomApplicationStatus.PENDING)
        apply(roomId, RoomApplicationStatus.ROOM_CANCELED)
        apply(roomId, RoomApplicationStatus.ROOM_CONFIRMED)
        apply(roomId, RoomApplicationStatus.WITHDRAWN)

        val listed = roomApplicationRepository.findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(
            roomId,
            RoomApplicationStatus.WITHDRAWN,
        )

        assertThat(listed.map { it.status }).containsExactlyInAnyOrder(
            RoomApplicationStatus.PENDING,
            RoomApplicationStatus.ROOM_CANCELED,
            RoomApplicationStatus.ROOM_CONFIRMED,
        )
    }

    // 대기 신청은 (room_id, pending_member_id) 유니크라 신청자를 매번 다르게 둔다.
    private fun apply(roomId: UUID, status: RoomApplicationStatus): RoomApplicationEntity {
        val applicantMemberId = UUID.randomUUID()
        return roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = applicantMemberId,
                note = "",
                appliedAt = now,
                status = status,
                pendingMemberId = applicantMemberId.takeIf { status == RoomApplicationStatus.PENDING },
            ),
        )
    }
}
