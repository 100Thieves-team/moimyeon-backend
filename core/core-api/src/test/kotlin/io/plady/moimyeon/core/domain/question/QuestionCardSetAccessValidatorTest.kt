package io.plady.moimyeon.core.domain.question

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestionCardSetAccessValidatorTest {
    private val roomFinder = mockk<RoomFinder>()
    private val participationFinder = mockk<ParticipationFinder>()
    private val validator = QuestionCardSetAccessValidator(roomFinder, participationFinder)

    private val roomId = UUID.randomUUID()
    private val requesterMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()

    @Test
    fun `확정된 룸의 현재 참여자는 카드셋을 조회할 수 있다`() {
        every { roomFinder.getRoom(roomId) } returns room(RoomStatus.CONFIRMED)
        every { participationFinder.isParticipating(roomId, requesterMemberId) } returns true

        assertThatCode {
            validator.validateViewer(roomId, requesterMemberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `완료된 룸의 현재 참여자는 카드셋을 조회할 수 있다`() {
        every { roomFinder.getRoom(roomId) } returns room(RoomStatus.COMPLETED)
        every { participationFinder.isParticipating(roomId, requesterMemberId) } returns true

        assertThatCode {
            validator.validateViewer(roomId, requesterMemberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `룸을 찾을 수 없으면 ROOM_NOT_FOUND 를 그대로 반환하고 참여 여부는 조회하지 않는다`() {
        every { roomFinder.getRoom(roomId) } throws CoreException(CoreErrorType.ROOM_NOT_FOUND)

        assertValidationFails(CoreErrorType.ROOM_NOT_FOUND) {
            validator.validateViewer(roomId, requesterMemberId)
        }
        verify(exactly = 0) { participationFinder.isParticipating(any(), any()) }
    }

    @Test
    fun `확정 전 룸이면 QUESTION_CARD_SET_NOT_OPEN 으로 거부하고 참여 여부는 조회하지 않는다`() {
        every { roomFinder.getRoom(roomId) } returns room(RoomStatus.RECRUITING)

        assertValidationFails(CoreErrorType.QUESTION_CARD_SET_NOT_OPEN) {
            validator.validateViewer(roomId, requesterMemberId)
        }
        verify(exactly = 0) { participationFinder.isParticipating(any(), any()) }
    }

    @Test
    fun `취소된 룸이면 QUESTION_CARD_SET_NOT_OPEN 으로 거부하고 참여 여부는 조회하지 않는다`() {
        every { roomFinder.getRoom(roomId) } returns room(RoomStatus.CANCELED)

        assertValidationFails(CoreErrorType.QUESTION_CARD_SET_NOT_OPEN) {
            validator.validateViewer(roomId, requesterMemberId)
        }
        verify(exactly = 0) { participationFinder.isParticipating(any(), any()) }
    }

    @Test
    fun `확정된 룸의 현재 참여자가 아니면 QUESTION_CARD_SET_FORBIDDEN 으로 거부한다`() {
        every { roomFinder.getRoom(roomId) } returns room(RoomStatus.CONFIRMED)
        every { participationFinder.isParticipating(roomId, requesterMemberId) } returns false

        assertValidationFails(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN) {
            validator.validateViewer(roomId, requesterMemberId)
        }
    }

    @Test
    fun `현재 참여 중인 다른 참여자의 카드셋이면 조회할 수 있다`() {
        every { participationFinder.wasConfirmedParticipant(roomId, targetMemberId) } returns true

        assertThatCode {
            validator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `확정 후 이탈한 대상의 카드셋은 유지되어 조회할 수 있다`() {
        every { participationFinder.isParticipating(roomId, targetMemberId) } returns false
        every { participationFinder.wasConfirmedParticipant(roomId, targetMemberId) } returns true

        assertThatCode {
            validator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
        }.doesNotThrowAnyException()

        verify(exactly = 0) { participationFinder.isParticipating(roomId, targetMemberId) }
    }

    @Test
    fun `자기 카드셋을 조회하면 QUESTION_CARD_SET_FORBIDDEN 으로 거부하고 대상 참여 여부는 조회하지 않는다`() {
        assertValidationFails(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN) {
            validator.validateOtherCardSetTarget(roomId, requesterMemberId, requesterMemberId)
        }
        verify(exactly = 0) { participationFinder.wasConfirmedParticipant(any(), any()) }
    }

    @Test
    fun `대상이 확정 시점 참여자가 아니면 QUESTION_CARD_SET_NOT_FOUND 로 거부한다`() {
        every { participationFinder.wasConfirmedParticipant(roomId, targetMemberId) } returns false

        assertValidationFails(CoreErrorType.QUESTION_CARD_SET_NOT_FOUND) {
            validator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
        }
    }

    private fun room(status: RoomStatus): Room {
        val room = mockk<Room>()
        every { room.status } returns status
        return room
    }

    private fun assertValidationFails(
        errorType: CoreErrorType,
        block: () -> Unit,
    ) {
        assertThatThrownBy(block)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
