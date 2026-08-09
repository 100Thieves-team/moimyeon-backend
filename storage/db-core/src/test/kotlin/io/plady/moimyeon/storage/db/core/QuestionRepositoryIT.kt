package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class QuestionRepositoryIT(
    private val questionRepository: QuestionRepository,
) : CoreDbContextTest() {
    private val roomId = UUID.randomUUID()
    private val firstTargetMemberId = UUID.randomUUID()
    private val secondTargetMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `여러 대상의 활성 질문을 한 번에 생성 순서로 조회한다`() {
        val firstRoot = saveQuestion(firstTargetMemberId)
        val followUp = saveQuestion(firstTargetMemberId, parentQuestionId = firstRoot.id)
        val secondRoot = saveQuestion(secondTargetMemberId)
        saveQuestion(UUID.randomUUID())
        saveQuestion(firstTargetMemberId, roomId = UUID.randomUUID())
        saveQuestion(firstTargetMemberId).also { it.delete(LocalDateTime.of(2026, 8, 10, 1, 0)) }
        questionRepository.flush()

        val result = questionRepository
            .findByRoomIdAndTargetMemberIdInAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                listOf(firstTargetMemberId, secondTargetMemberId),
            )

        assertThat(result.map { it.id }).containsExactly(firstRoot.id, followUp.id, secondRoot.id)
    }

    @Test
    fun `한 대상의 활성 질문만 생성 순서로 조회한다`() {
        val root = saveQuestion(firstTargetMemberId)
        val followUp = saveQuestion(firstTargetMemberId, parentQuestionId = root.id)
        saveQuestion(secondTargetMemberId)

        val result = questionRepository
            .findByRoomIdAndTargetMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                firstTargetMemberId,
            )

        assertThat(result.map { it.id }).containsExactly(root.id, followUp.id)
        assertThat(result.map { it.source }).containsExactly(
            QuestionSource.PREPARATION,
            QuestionSource.IN_PROGRESS,
        )
    }

    private fun saveQuestion(
        targetMemberId: UUID,
        roomId: UUID = this.roomId,
        parentQuestionId: Long? = null,
    ): QuestionEntity = questionRepository.saveAndFlush(
        QuestionEntity(
            roomId = roomId,
            targetMemberId = targetMemberId,
            authorMemberId = authorMemberId,
            parentQuestionId = parentQuestionId,
            content = "질문",
            source = if (parentQuestionId == null) QuestionSource.PREPARATION else QuestionSource.IN_PROGRESS,
        ),
    )
}
