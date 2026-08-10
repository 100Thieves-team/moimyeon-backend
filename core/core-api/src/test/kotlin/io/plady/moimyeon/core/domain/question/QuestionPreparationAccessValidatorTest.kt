package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomDetail
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestionPreparationAccessValidatorTest {
    private val roomFinder = mockk<RoomFinder>()
    private val participationFinder = mockk<ParticipationFinder>()
    private val validator = QuestionPreparationAccessValidator(roomFinder, participationFinder)

    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()

    @Test
    fun `확정된 룸의 현재 참여자는 준비 질문을 남길 수 있다`() {
        every { roomFinder.getRoom(roomId) } returns roomDetail(RoomStatus.CONFIRMED)
        every { participationFinder.isParticipating(roomId, authorMemberId) } returns true

        assertThatCode {
            validator.validateAuthor(roomId, authorMemberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `완료된 룸에서는 준비 질문을 새로 남길 수 없고 E1504 를 던진다`() {
        every { roomFinder.getRoom(roomId) } returns roomDetail(RoomStatus.COMPLETED)

        assertValidationFails(CoreErrorType.QUESTION_PREPARATION_NOT_OPEN) {
            validator.validateAuthor(roomId, authorMemberId)
        }

        verify(exactly = 0) { participationFinder.isParticipating(any(), any()) }
    }

    @Test
    fun `확정 전 룸에서는 준비 질문을 남길 수 없고 E1504 를 던진다`() {
        every { roomFinder.getRoom(roomId) } returns roomDetail(RoomStatus.RECRUITING)

        assertValidationFails(CoreErrorType.QUESTION_PREPARATION_NOT_OPEN) {
            validator.validateAuthor(roomId, authorMemberId)
        }

        verify(exactly = 0) { participationFinder.isParticipating(any(), any()) }
    }

    @Test
    fun `현재 참여자가 아니면 준비 질문을 남길 수 없고 E1505 를 던진다`() {
        every { roomFinder.getRoom(roomId) } returns roomDetail(RoomStatus.CONFIRMED)
        every { participationFinder.isParticipating(roomId, authorMemberId) } returns false

        assertValidationFails(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN) {
            validator.validateAuthor(roomId, authorMemberId)
        }
    }

    @Test
    fun `자신을 질문 대상으로 지정하면 E1505 를 던지고 확정 명단을 조회하지 않는다`() {
        assertValidationFails(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN) {
            validator.validateTarget(roomId, authorMemberId, authorMemberId)
        }

        verify(exactly = 0) { participationFinder.wasConfirmedParticipant(any(), any()) }
    }

    @Test
    fun `룸 확정 시점 참여자였던 다른 사람에게 질문을 남길 수 있다`() {
        every { participationFinder.wasConfirmedParticipant(roomId, targetMemberId) } returns true

        assertThatCode {
            validator.validateTarget(roomId, authorMemberId, targetMemberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `질문 대상이 확정 시점 참여자가 아니면 E1506 을 던진다`() {
        every { participationFinder.wasConfirmedParticipant(roomId, targetMemberId) } returns false

        assertValidationFails(CoreErrorType.QUESTION_TARGET_NOT_FOUND) {
            validator.validateTarget(roomId, authorMemberId, targetMemberId)
        }
    }

    private fun roomDetail(status: RoomStatus): RoomDetail {
        val room = mockk<Room>()
        every { room.status } returns status
        return RoomDetail(room, UUID.randomUUID(), 4)
    }

    private fun assertValidationFails(errorType: CoreErrorType, block: () -> Unit) {
        assertThatThrownBy(block)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
