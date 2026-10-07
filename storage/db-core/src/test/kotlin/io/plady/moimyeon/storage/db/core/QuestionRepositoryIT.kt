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

    @Test
    fun `삭제된 질문은 단건 활성 조회와 삭제 잠금 조회에서 찾지 않는다`() {
        val question = saveQuestion(firstTargetMemberId)
        question.delete(LocalDateTime.of(2026, 8, 10, 3, 0))
        questionRepository.flush()

        assertThat(questionRepository.findByIdAndDeletedAtIsNull(question.id)).isNull()
        assertThat(
            questionRepository.findForUpdateByRoomIdAndIdAndDeletedAtIsNull(roomId, question.id),
        ).isNull()
    }

    @Test
    fun `작성자별 활성 꼬리질문을 구분하고 다른 작성자의 꼬리질문만 원 질문 삭제를 막는다`() {
        val root = saveQuestion(firstTargetMemberId)
        val ownFollowUp = saveQuestion(firstTargetMemberId, parentQuestionId = root.id)
        val otherAuthorFollowUp = saveQuestion(
            firstTargetMemberId,
            parentQuestionId = root.id,
            authorMemberId = UUID.randomUUID(),
        )

        assertThat(
            questionRepository.existsByParentQuestionIdAndAuthorMemberIdNotAndDeletedAtIsNull(
                root.id,
                authorMemberId,
            ),
        ).isTrue()
        assertThat(
            questionRepository.findAllByParentQuestionIdAndAuthorMemberIdAndDeletedAtIsNull(
                root.id,
                authorMemberId,
            ).map { it.id },
        ).containsExactly(ownFollowUp.id)

        otherAuthorFollowUp.delete(LocalDateTime.of(2026, 8, 10, 3, 0))
        questionRepository.flush()

        assertThat(
            questionRepository.existsByParentQuestionIdAndAuthorMemberIdNotAndDeletedAtIsNull(
                root.id,
                authorMemberId,
            ),
        ).isFalse()
    }

    @Test
    fun `본인 카드셋 준비 인원은 원 질문과 꼬리질문의 활성 작성자를 중복 없이 센다`() {
        val root = saveQuestion(firstTargetMemberId)
        saveQuestion(firstTargetMemberId, parentQuestionId = root.id)
        saveQuestion(firstTargetMemberId, authorMemberId = UUID.randomUUID())
        saveQuestion(secondTargetMemberId, authorMemberId = UUID.randomUUID())
        saveQuestion(firstTargetMemberId, authorMemberId = UUID.randomUUID())
            .also { it.delete(LocalDateTime.of(2026, 8, 10, 4, 0)) }
        questionRepository.flush()

        val result = questionRepository
            .countDistinctAuthorsByRoomIdAndTargetMemberIdAndDeletedAtIsNull(roomId, firstTargetMemberId)

        assertThat(result).isEqualTo(2)
    }

    private fun saveQuestion(
        targetMemberId: UUID,
        roomId: UUID = this.roomId,
        parentQuestionId: Long? = null,
        authorMemberId: UUID = this.authorMemberId,
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
