package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.CoreDbContextTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class ParticipationRepositoryIT(
    private val participationRepository: ParticipationRepository,
    private val entityManager: EntityManager,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)

    @Test
    fun `여러 룸의 활성 참여 수를 한 번에 집계한다`() {
        val busy = UUID.randomUUID()
        val quiet = UUID.randomUUID()
        repeat(3) { join(busy) }
        join(quiet)

        val counts = participationRepository.countActiveByRoomIds(listOf(busy, quiet)).associate { it.roomId to it.count }

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(mapOf(busy to 3L, quiet to 1L))
    }

    // 현재 인원은 정원 확정·탐색 목록과 같은 술어여야 한다. 나간 사람의 자리는 비워진 것으로 본다.
    @Test
    fun `나간 참여는 집계에서 제외한다`() {
        val roomId = UUID.randomUUID()
        join(roomId)
        join(roomId, status = ParticipationStatus.LEFT)

        assertThat(participationRepository.countActiveByRoomIds(listOf(roomId)).single().count).isEqualTo(1)
    }

    @Test
    fun `삭제된 참여는 집계에서 제외한다`() {
        val roomId = UUID.randomUUID()
        join(roomId)
        join(roomId).also { it.delete(now) }
        entityManager.flush()

        assertThat(participationRepository.countActiveByRoomIds(listOf(roomId)).single().count).isEqualTo(1)
    }

    @Test
    fun `조회 대상이 아닌 룸의 참여는 집계에 섞이지 않는다`() {
        val target = UUID.randomUUID()
        val other = UUID.randomUUID()
        join(target)
        repeat(5) { join(other) }

        val counts = participationRepository.countActiveByRoomIds(listOf(target))

        assertThat(counts).singleElement().satisfies({ assertThat(it.roomId).isEqualTo(target) })
    }

    // 이 계약을 호출자가 알아야 한다. 0 을 채우지 않으면 응답 조립에서 키가 없어 터진다.
    @Test
    fun `참여가 없는 룸은 결과에 아예 나오지 않는다`() {
        val empty = UUID.randomUUID()

        assertThat(participationRepository.countActiveByRoomIds(listOf(empty))).isEmpty()
    }

    @Test
    fun `확정 후 이탈한 참여자는 확정 명단에 남아 있다`() {
        val roomId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        recordConfirmation(roomId, now)
        join(
            roomId = roomId,
            memberId = memberId,
            status = ParticipationStatus.LEFT,
            joinedAt = now.minusDays(1),
            leftAt = now.plusHours(1),
        )

        assertThat(participationRepository.existsAtRoomConfirmation(roomId, memberId)).isTrue()
    }

    @Test
    fun `확정 전에 이탈한 참여자는 확정 명단에서 제외한다`() {
        val roomId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        recordConfirmation(roomId, now)
        join(
            roomId = roomId,
            memberId = memberId,
            status = ParticipationStatus.LEFT,
            joinedAt = now.minusDays(1),
            leftAt = now.minusHours(1),
        )

        assertThat(participationRepository.existsAtRoomConfirmation(roomId, memberId)).isFalse()
    }

    @Test
    fun `운영상 삭제된 참여 행은 확정 명단에서 제외한다`() {
        val roomId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        recordConfirmation(roomId, now)
        join(
            roomId = roomId,
            memberId = memberId,
            status = ParticipationStatus.JOINED,
            joinedAt = now.minusDays(1),
        ).also { it.delete(now.plusHours(1)) }
        entityManager.flush()

        assertThat(participationRepository.existsAtRoomConfirmation(roomId, memberId)).isFalse()
    }

    @Test
    fun `확정 참여자 목록은 확정 순간 참여자만 joinedAt 오름차순과 id 오름차순으로 반환한다`() {
        val roomId = UUID.randomUUID()
        val first = join(
            roomId = roomId,
            memberId = UUID.randomUUID(),
            status = ParticipationStatus.LEFT,
            joinedAt = now.minusDays(2),
            leftAt = now.plusHours(1),
        )
        val second = join(
            roomId = roomId,
            memberId = UUID.randomUUID(),
            status = ParticipationStatus.JOINED,
            joinedAt = now.minusDays(1),
        )
        val third = join(
            roomId = roomId,
            memberId = UUID.randomUUID(),
            status = ParticipationStatus.JOINED,
            joinedAt = now.minusDays(1),
        )
        join(
            roomId = roomId,
            memberId = UUID.randomUUID(),
            status = ParticipationStatus.LEFT,
            joinedAt = now.minusDays(3),
            leftAt = now.minusHours(1),
        )
        join(
            roomId = roomId,
            memberId = UUID.randomUUID(),
            status = ParticipationStatus.JOINED,
            joinedAt = now.plusHours(1),
        )
        recordConfirmation(roomId, now)

        val result = participationRepository.findAllAtRoomConfirmation(roomId)

        assertThat(result.map { it.memberId }).containsExactly(first.memberId, second.memberId, third.memberId)
    }

    // 자진 이탈 후 재신청은 정상 흐름이다(MOI-397). 유니크가 상태를 보지 않으면 신청은 받아 놓고
    // 방장이 수락하는 순간 무결성 위반으로 터진다 — 되돌릴 수 없는 자리에서 실패한다.
    @Test
    fun `나간 사람이 같은 룸에 다시 참여할 수 있다`() {
        val roomId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        join(roomId, memberId, ParticipationStatus.LEFT, joinedAt = now.minusDays(1), leftAt = now)

        join(roomId, memberId, ParticipationStatus.JOINED)

        assertThat(participationRepository.countActiveByRoomIds(listOf(roomId)).single().count).isEqualTo(1)
    }

    // 좁힌 유니크가 여전히 이중 참여는 막는지. 이게 뚫리면 정원이 무너진다.
    @Test
    fun `같은 룸에 JOINED 참여를 두 번 만들 수 없다`() {
        val roomId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        join(roomId, memberId, ParticipationStatus.JOINED)

        assertThatThrownBy { join(roomId, memberId, ParticipationStatus.JOINED) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    // 뷰어 관계 일괄 조회(MOI-387). 목록 한 페이지의 룸 전부에 대해 내 참여 행을 한 번에 읽는다.
    @Test
    fun `여러 룸의 내 참여 행을 한 번에 읽고 방장과 참여자를 구분한다`() {
        val viewerId = UUID.randomUUID()
        val hosted = UUID.randomUUID()
        val joined = UUID.randomUUID()
        val stranger = UUID.randomUUID()
        join(hosted, viewerId, ParticipationStatus.JOINED, participationRole = ParticipationRole.HOST)
        join(joined, viewerId, ParticipationStatus.JOINED)
        join(stranger, UUID.randomUUID(), ParticipationStatus.JOINED)

        val rows = participationRepository.findByMemberIdAndRoomIdIn(viewerId, listOf(hosted, joined, stranger))

        assertThat(rows.associate { it.roomId to it.participationRole })
            .containsExactlyInAnyOrderEntriesOf(
                mapOf(hosted to ParticipationRole.HOST, joined to ParticipationRole.PARTICIPANT),
            )
    }

    // 재신청을 막는 것은 강퇴뿐이다(existsRemovalHistory 와 같은 기준). 호출자가 둘을 갈라야 하므로
    // 이 조회는 LEFT 행을 걸러내지 않고 그대로 돌려준다.
    @Test
    fun `내보내진 이력과 자진 이탈을 구분해 읽는다`() {
        val viewerId = UUID.randomUUID()
        val removedFrom = UUID.randomUUID()
        val leftBySelf = UUID.randomUUID()
        join(removedFrom, viewerId, ParticipationStatus.LEFT, leftAt = now, leftByMemberId = UUID.randomUUID())
        join(leftBySelf, viewerId, ParticipationStatus.LEFT, leftAt = now, leftByMemberId = viewerId)

        val rows = participationRepository.findByMemberIdAndRoomIdIn(viewerId, listOf(removedFrom, leftBySelf))

        assertThat(rows.single { it.roomId == removedFrom }.leftByMemberId).isNotEqualTo(viewerId)
        assertThat(rows.single { it.roomId == leftBySelf }.leftByMemberId).isEqualTo(viewerId)
    }

    private fun join(
        roomId: UUID,
        status: ParticipationStatus = ParticipationStatus.JOINED,
    ): ParticipationEntity = join(roomId, UUID.randomUUID(), status)

    private fun join(
        roomId: UUID,
        memberId: UUID,
        status: ParticipationStatus,
        joinedAt: LocalDateTime = now,
        leftAt: LocalDateTime? = null,
        participationRole: ParticipationRole = ParticipationRole.PARTICIPANT,
        leftByMemberId: UUID? = null,
    ): ParticipationEntity = participationRepository.saveAndFlush(
        ParticipationEntity(
            roomId = roomId,
            memberId = memberId,
            participationRole = participationRole,
            status = status,
            joinedAt = joinedAt,
            leftAt = leftAt,
            leftByMemberId = leftByMemberId,
        ),
    )

    private fun recordConfirmation(roomId: UUID, occurredAt: LocalDateTime) {
        entityManager.createNativeQuery(
            """
            insert into room_status_log (
                room_id, transition_type, handler_member_id, occurred_at,
                created_at, updated_at, deleted_at
            ) values (
                :roomId, 'CONFIRMED', :handlerMemberId, :occurredAt,
                :occurredAt, :occurredAt, null
            )
            """.trimIndent(),
        )
            .setParameter("roomId", roomId)
            .setParameter("handlerMemberId", UUID.randomUUID())
            .setParameter("occurredAt", occurredAt)
            .executeUpdate()
    }
}
