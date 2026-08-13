package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.CoreDbContextTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoomApplicationRepositoryIT(
    private val roomApplicationRepository: RoomApplicationRepository,
    private val entityManager: EntityManager,
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

    @Test
    fun `룸의 대기 신청을 한 번에 ROOM_CANCELED 로 종료한다`() {
        val roomId = UUID.randomUUID()
        repeat(3) { apply(roomId, RoomApplicationStatus.PENDING) }

        val closed = closeAllPending(roomId)

        assertThat(closed).isEqualTo(3)
        assertThat(reload(roomId).map { it.status })
            .containsOnly(RoomApplicationStatus.ROOM_CANCELED)
    }

    // 이걸 빠뜨리면 대기 유니크 자리가 잠긴 채 남아 그 신청자는 어느 룸에도 자리를 못 되찾는다.
    @Test
    fun `종료된 신청은 pending_member_id 가 풀려 대기 한도에서 빠진다`() {
        val roomId = UUID.randomUUID()
        val applicant = apply(roomId, RoomApplicationStatus.PENDING).applicantMemberId

        closeAllPending(roomId)

        assertThat(reload(roomId).single().pendingMemberId).isNull()
        assertThat(
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
                applicant,
                RoomApplicationStatus.PENDING,
            ),
        ).isZero()
    }

    @Test
    fun `이미 처리된 신청과 내려간 신청은 종료 대상이 아니다`() {
        val roomId = UUID.randomUUID()
        apply(roomId, RoomApplicationStatus.PENDING)
        apply(roomId, RoomApplicationStatus.ACCEPTED)
        apply(roomId, RoomApplicationStatus.REJECTED)
        apply(roomId, RoomApplicationStatus.WITHDRAWN)
        apply(roomId, RoomApplicationStatus.ROOM_CONFIRMED)
        apply(roomId, RoomApplicationStatus.PENDING).also { it.delete(now) }
        roomApplicationRepository.flush()

        val closed = closeAllPending(roomId)

        assertThat(closed).isEqualTo(1)
        assertThat(roomApplicationRepository.findAll().filter { it.roomId == roomId }.map { it.status })
            .containsExactlyInAnyOrder(
                RoomApplicationStatus.ROOM_CANCELED,
                RoomApplicationStatus.ACCEPTED,
                RoomApplicationStatus.REJECTED,
                RoomApplicationStatus.WITHDRAWN,
                RoomApplicationStatus.ROOM_CONFIRMED,
                RoomApplicationStatus.PENDING,
            )
    }

    @Test
    fun `다른 룸의 대기 신청은 건드리지 않는다`() {
        val canceled = UUID.randomUUID()
        val untouched = UUID.randomUUID()
        apply(canceled, RoomApplicationStatus.PENDING)
        apply(untouched, RoomApplicationStatus.PENDING)

        closeAllPending(canceled)

        assertThat(reload(untouched).single().status).isEqualTo(RoomApplicationStatus.PENDING)
    }

    // handler_member_id 를 채우면 방장 목록에서 반려와 구별되지 않는다(「룸 참여」 §6).
    // 여기서 NULL 은 "사람이 처리하지 않았다"는 뜻 하나로 유지된다.
    @Test
    fun `종료된 신청은 handled_at 과 updated_at 이 갱신되고 handler_member_id 는 비어 있다`() {
        val roomId = UUID.randomUUID()
        apply(roomId, RoomApplicationStatus.PENDING)

        closeAllPending(roomId)

        val closed = reload(roomId).single()
        assertThat(closed.handledAt).isEqualTo(now)
        assertThat(closed.updatedAt).isEqualTo(now)
        assertThat(closed.handlerMemberId).isNull()
        assertThat(closed.rejectReason).isNull()
    }

    @Test
    fun `대기 신청이 없으면 아무 행도 바뀌지 않는다`() {
        val roomId = UUID.randomUUID()
        apply(roomId, RoomApplicationStatus.ACCEPTED)

        assertThat(closeAllPending(roomId)).isZero()
    }

    // 벌크는 영속성 컨텍스트를 우회하므로 비우고 다시 읽어야 실제 저장된 값이 보인다.
    private fun closeAllPending(roomId: UUID): Int {
        val closed = roomApplicationRepository.closeAllPending(roomId, RoomApplicationStatus.ROOM_CANCELED, now)
        entityManager.clear()
        return closed
    }

    private fun reload(roomId: UUID) = roomApplicationRepository.findAll().filter { it.roomId == roomId }

    // 뷰어 관계 일괄 조회(MOI-387). 목록과 상세가 다른 관계를 말하면 안 되므로,
    // 룸별 "최신 한 건"이 단건 조회 경로와 같은 행이어야 한다.
    @Test
    fun `여러 룸의 내 신청을 한 번에 읽고 룸별 최신 한 건이 단건 조회와 같다`() {
        val applicantId = UUID.randomUUID()
        val reapplied = UUID.randomUUID()
        val rejected = UUID.randomUUID()
        apply(reapplied, RoomApplicationStatus.WITHDRAWN, applicantId, appliedAt = now.minusDays(1))
        apply(reapplied, RoomApplicationStatus.PENDING, applicantId, appliedAt = now)
        apply(rejected, RoomApplicationStatus.REJECTED, applicantId, appliedAt = now)

        val latestByRoom = roomApplicationRepository
            .findByApplicantMemberIdAndRoomIdInAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                applicantId,
                listOf(reapplied, rejected),
            )
            .groupBy { it.roomId }
            .mapValues { (_, applications) -> applications.first() }

        listOf(reapplied, rejected).forEach { roomId ->
            val single = roomApplicationRepository
                .findFirstByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(roomId, applicantId)
            assertThat(latestByRoom[roomId]?.id).describedAs("$roomId").isEqualTo(single?.id)
        }
        assertThat(latestByRoom[reapplied]?.status).isEqualTo(RoomApplicationStatus.PENDING)
    }

    // 대기 신청은 (room_id, pending_member_id) 유니크라 신청자를 매번 다르게 둔다.
    private fun apply(roomId: UUID, status: RoomApplicationStatus): RoomApplicationEntity = apply(
        roomId,
        status,
        UUID.randomUUID(),
    )

    private fun apply(
        roomId: UUID,
        status: RoomApplicationStatus,
        applicantMemberId: UUID,
        appliedAt: LocalDateTime = now,
    ): RoomApplicationEntity = roomApplicationRepository.saveAndFlush(
        RoomApplicationEntity(
            roomId = roomId,
            applicantMemberId = applicantMemberId,
            note = "",
            appliedAt = appliedAt,
            status = status,
            pendingMemberId = applicantMemberId.takeIf { status == RoomApplicationStatus.PENDING },
        ),
    )
}
