package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.CoreDbContextTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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

    private fun join(roomId: UUID): ParticipationEntity = participationRepository.saveAndFlush(
        ParticipationEntity(
            roomId = roomId,
            memberId = UUID.randomUUID(),
            participationRole = ParticipationRole.PARTICIPANT,
            status = ParticipationStatus.JOINED,
            joinedAt = now,
        ),
    )
}
