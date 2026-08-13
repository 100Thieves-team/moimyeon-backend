package io.plady.moimyeon.core.domain.roundfeedback

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RoundFeedbackEntity
import io.plady.moimyeon.storage.db.core.RoundFeedbackRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class RoundFeedbackManagerTest {
    private val repository = mockk<RoundFeedbackRepository>()
    private val now = LocalDateTime.of(2026, 8, 14, 12, 0)
    private val manager = RoundFeedbackManager(
        repository,
        Clock.fixed(Instant.parse("2026-08-14T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )
    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `최종 피드백은 같은 작성자의 활성 행이 있으면 수정하지 않고 거부한다`() {
        val existing = feedbackEntity(type = RoundFeedbackType.FINAL)
        every {
            repository.findForUpdateByRoomIdAndIntervieweeMemberIdAndAuthorMemberIdAndDeletedAtIsNull(
                roomId,
                intervieweeMemberId,
                authorMemberId,
            )
        } returns existing

        assertThatThrownBy { manager.save(command(RoundFeedbackType.FINAL, "새 피드백")) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS)
            }
        assertThat(existing.content).isEqualTo("기존 피드백")
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `자가 피드백은 같은 작성자의 활성 행을 수정하고 식별자를 유지한다`() {
        val existing = feedbackEntity(type = RoundFeedbackType.SELF)
        every {
            repository.findForUpdateByRoomIdAndIntervieweeMemberIdAndAuthorMemberIdAndDeletedAtIsNull(
                roomId,
                intervieweeMemberId,
                intervieweeMemberId,
            )
        } returns existing

        val result = manager.save(
            command(
                type = RoundFeedbackType.SELF,
                content = "수정한 자가 피드백",
                authorMemberId = intervieweeMemberId,
            ),
        )

        assertThat(result).isEqualTo(existing.id)
        assertThat(existing.content).isEqualTo("수정한 자가 피드백")
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `이미 열람한 최종 피드백을 다시 확인해도 성공하고 최초 시각을 유지한다`() {
        val disclosedAt = now.minusMinutes(3)
        val feedback = feedbackEntity(type = RoundFeedbackType.FINAL).also { it.disclose(disclosedAt) }
        every {
            repository.findForUpdateByRoomIdAndIntervieweeMemberIdAndIdAndFeedbackTypeAndDeletedAtIsNull(
                roomId,
                intervieweeMemberId,
                feedback.id,
                RoundFeedbackType.FINAL,
            )
        } returns feedback

        manager.confirmDisclosure(roomId, intervieweeMemberId, feedback.id)

        assertThat(feedback.disclosedAt).isEqualTo(disclosedAt)
    }

    private fun command(
        type: RoundFeedbackType,
        content: String,
        authorMemberId: UUID = this.authorMemberId,
    ) = RoundFeedbackCommand(roomId, intervieweeMemberId, authorMemberId, type, content)

    private fun feedbackEntity(type: RoundFeedbackType) = RoundFeedbackEntity(
        roomId = roomId,
        intervieweeMemberId = intervieweeMemberId,
        authorMemberId = if (type == RoundFeedbackType.SELF) intervieweeMemberId else authorMemberId,
        feedbackType = type,
        content = "기존 피드백",
    )
}
