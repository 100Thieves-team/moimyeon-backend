package io.plady.moimyeon.core.domain.question

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class QuestionCardSetLifecycleIT(
    private val questionRecorder: QuestionRecorder,
    private val questionCardSetReader: QuestionCardSetReader,
    private val participationRepository: ParticipationRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val confirmationTime = LocalDateTime.of(2026, 8, 10, 3, 0)
    private val authorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val viewerMemberId = UUID.randomUUID()

    @Test
    fun `확정 후 이탈 상태인 작성자의 질문과 작성자 식별자는 카드셋에 유지된다`() {
        recordConfirmation()
        join(
            memberId = authorMemberId,
            status = ParticipationStatus.LEFT,
            leftAt = confirmationTime.plusHours(1),
        )
        join(targetMemberId)
        join(viewerMemberId)
        val questionId = questionRecorder.record(
            roomId,
            targetMemberId,
            authorMemberId,
            null,
            "이탈 뒤에도 남아야 하는 질문",
            QuestionSource.PREPARATION,
        )

        val targetCardSet = questionCardSetReader
            .getAllByRoomExceptTarget(roomId, viewerMemberId)
            .single { it.targetMemberId == targetMemberId }
        val question = targetCardSet.questions.single()

        assertThat(question.id).isEqualTo(questionId)
        assertThat(question.authorMemberId).isEqualTo(authorMemberId)
        assertThat(question.content).isEqualTo("이탈 뒤에도 남아야 하는 질문")
    }

    private fun join(
        memberId: UUID,
        status: ParticipationStatus = ParticipationStatus.JOINED,
        leftAt: LocalDateTime? = null,
    ) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = status,
                joinedAt = confirmationTime.minusDays(1),
                leftByMemberId = memberId.takeIf { status == ParticipationStatus.LEFT },
                leftAt = leftAt,
            ),
        )
    }

    private fun recordConfirmation() {
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
            .setParameter("occurredAt", confirmationTime)
            .executeUpdate()
    }
}
