package io.plady.moimyeon.core.domain.roundfeedback

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoundFeedbackEntity
import io.plady.moimyeon.storage.db.core.RoundFeedbackRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class RoundFeedbackManagerTest {
    private val roomId = UUID.randomUUID()
    private val intervieweeMemberId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val roomRepository = mockk<RoomRepository>()
    private val feedbackRepository = mockk<RoundFeedbackRepository>()
    private val room = mockk<RoomEntity> {
        every { isActive() } returns true
        every { status } returns RoomStatus.IN_PROGRESS
    }
    private val now = LocalDateTime.of(2026, 8, 14, 12, 0)
    private val manager = RoundFeedbackManager(
        roomRepository,
        feedbackRepository,
        Clock.fixed(Instant.parse("2026-08-14T03:00:00Z"), ZoneId.of("Asia/Seoul")),
    )

    init {
        every { roomRepository.findByIdForUpdate(roomId) } returns room
    }

    @Test
    fun `최종 피드백은 같은 작성자의 활성 행이 있으면 수정하지 않고 거부한다`() {
        val existing = feedbackEntity(type = RoundFeedbackType.FINAL)
        every { findByAuthor(authorMemberId) } returns existing

        assertThatThrownBy { manager.registerFinalFeedback(command("새 피드백")) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS)
            }
        assertThat(existing.content).isEqualTo("기존 피드백")
        verify(exactly = 0) { feedbackRepository.saveAndFlush(any()) }
    }

    @Test
    fun `최종 피드백의 예상한 유니크 충돌만 중복 등록 오류로 번역한다`() {
        every { findByAuthor(authorMemberId) } returns null
        every { feedbackRepository.saveAndFlush(any()) } throws
            DataIntegrityViolationException("uk_round_feedback_round_author_active")

        assertThatThrownBy { manager.registerFinalFeedback(command("최종 피드백")) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS)
            }
    }

    @Test
    fun `최종 피드백의 예상하지 않은 무결성 오류는 중복으로 오인하지 않는다`() {
        val unexpected = DataIntegrityViolationException("NULL not allowed for column CONTENT")
        every { findByAuthor(authorMemberId) } returns null
        every { feedbackRepository.saveAndFlush(any()) } throws unexpected

        assertThatThrownBy { manager.registerFinalFeedback(command("최종 피드백")) }
            .isSameAs(unexpected)
    }

    @Test
    fun `자가 피드백은 같은 작성자의 활성 행을 수정하고 식별자를 유지한다`() {
        val existing = feedbackEntity(type = RoundFeedbackType.SELF)
        every { findByAuthor(intervieweeMemberId) } returns existing

        val result = manager.upsertSelfFeedback(
            command(
                content = "수정한 자가 피드백",
                authorMemberId = intervieweeMemberId,
            ),
        )

        assertThat(result).isEqualTo(existing.id)
        assertThat(existing.content).isEqualTo("수정한 자가 피드백")
        verify(exactly = 0) { feedbackRepository.saveAndFlush(any()) }
    }

    @Test
    fun `자가 피드백 최초 저장은 룸 행을 먼저 잠가 동시 생성을 직렬화한다`() {
        val saved = feedbackEntity(type = RoundFeedbackType.SELF)
        val entitySlot = slot<RoundFeedbackEntity>()
        every { findByAuthor(intervieweeMemberId) } returns null
        every { feedbackRepository.saveAndFlush(capture(entitySlot)) } returns saved

        manager.upsertSelfFeedback(
            command(
                content = "최초 자가 피드백",
                authorMemberId = intervieweeMemberId,
            ),
        )

        assertThat(entitySlot.captured.feedbackType).isEqualTo(RoundFeedbackType.SELF)
        verifyOrder {
            roomRepository.findByIdForUpdate(roomId)
            findByAuthor(intervieweeMemberId)
            feedbackRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `룸 종료와 피드백 저장이 경합하면 잠금 뒤 상태를 다시 확인해 저장하지 않는다`() {
        every { room.status } returns RoomStatus.COMPLETED

        assertThatThrownBy {
            manager.upsertSelfFeedback(
                command(
                    content = "종료 뒤에는 저장되지 않을 피드백",
                    authorMemberId = intervieweeMemberId,
                ),
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROUND_FEEDBACK_NOT_EDITABLE)
        }
        verify(exactly = 0) {
            findByAuthor(intervieweeMemberId)
            feedbackRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `이미 열람한 최종 피드백을 다시 확인해도 성공하고 최초 시각을 유지한다`() {
        val disclosedAt = now.minusMinutes(3)
        val feedback = feedbackEntity(type = RoundFeedbackType.FINAL).also { it.disclose(disclosedAt) }
        every {
            feedbackRepository.findForUpdateByRoomIdAndIntervieweeMemberIdAndIdAndFeedbackTypeAndDeletedAtIsNull(
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
        content: String,
        authorMemberId: UUID = this.authorMemberId,
    ) = RoundFeedbackCommand(roomId, intervieweeMemberId, authorMemberId, content)

    private fun findByAuthor(authorMemberId: UUID): RoundFeedbackEntity? = feedbackRepository
        .findForUpdateByRoomIdAndIntervieweeMemberIdAndAuthorMemberIdAndDeletedAtIsNull(
            roomId,
            intervieweeMemberId,
            authorMemberId,
        )

    private fun feedbackEntity(type: RoundFeedbackType) = RoundFeedbackEntity(
        roomId = roomId,
        intervieweeMemberId = intervieweeMemberId,
        authorMemberId = if (type == RoundFeedbackType.SELF) intervieweeMemberId else authorMemberId,
        feedbackType = type,
        content = "기존 피드백",
    )
}
