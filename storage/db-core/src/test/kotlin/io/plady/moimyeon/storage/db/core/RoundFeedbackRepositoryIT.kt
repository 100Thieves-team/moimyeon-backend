package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoundFeedbackRepositoryIT(
    private val repository: RoundFeedbackRepository,
) : CoreDbContextTest() {
    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `라운드 피드백은 룸과 면접자와 작성자 조합에 활성 행 하나만 둔다`() {
        saveFeedback()

        assertThatThrownBy { saveFeedback() }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `선택한 룸과 면접자의 활성 피드백만 생성 순서로 조회한다`() {
        val first = saveFeedback()
        val second = saveFeedback(
            authorMemberId = intervieweeMemberId,
            type = RoundFeedbackType.SELF,
        )
        saveFeedback(roomId = UUID.randomUUID(), authorMemberId = UUID.randomUUID())
        saveFeedback(authorMemberId = UUID.randomUUID()).also {
            it.delete(LocalDateTime.of(2026, 8, 14, 11, 0))
        }
        repository.flush()

        val result = repository
            .findAllByRoomIdAndIntervieweeMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                intervieweeMemberId,
            )

        assertThat(result.map { it.id }).containsExactly(first.id, second.id)
    }

    @Test
    fun `최종 피드백 열람 재확인은 최초 시각을 유지한다`() {
        val feedback = saveFeedback()
        val firstDisclosedAt = LocalDateTime.of(2026, 8, 14, 12, 0)
        feedback.disclose(firstDisclosedAt)
        feedback.disclose(firstDisclosedAt.plusMinutes(1))
        repository.flush()

        val found = repository
            .findForUpdateByRoomIdAndIntervieweeMemberIdAndIdAndFeedbackTypeAndDeletedAtIsNull(
                roomId,
                intervieweeMemberId,
                feedback.id,
                RoundFeedbackType.FINAL,
            )

        assertThat(found?.disclosedAt).isEqualTo(firstDisclosedAt)
    }

    private fun saveFeedback(
        roomId: UUID = this.roomId,
        intervieweeMemberId: UUID = this.intervieweeMemberId,
        authorMemberId: UUID = this.authorMemberId,
        type: RoundFeedbackType = RoundFeedbackType.FINAL,
    ): RoundFeedbackEntity = repository.saveAndFlush(
        RoundFeedbackEntity(
            roomId = roomId,
            intervieweeMemberId = intervieweeMemberId,
            authorMemberId = authorMemberId,
            feedbackType = type,
            content = "피드백",
        ),
    )
}
