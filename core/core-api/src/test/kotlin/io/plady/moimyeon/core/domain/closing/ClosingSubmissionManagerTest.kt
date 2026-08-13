package io.plady.moimyeon.core.domain.closing

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.QuestionVote
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ClosingQuestionRepository
import io.plady.moimyeon.storage.db.core.ClosingResponseEntity
import io.plady.moimyeon.storage.db.core.ClosingResponseRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ClosingSubmissionManagerTest {
    private val roomRepository = mockk<RoomRepository>()
    private val attendanceRepository = mockk<AttendanceRepository>()
    private val closingQuestionRepository = mockk<ClosingQuestionRepository>()
    private val closingResponseRepository = mockk<ClosingResponseRepository>()
    private val manager = ClosingSubmissionManager(
        roomRepository,
        attendanceRepository,
        closingQuestionRepository,
        closingResponseRepository,
    )

    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val submittedAt = LocalDateTime.of(2026, 8, 13, 3, 0)
    private val command = ClosingSubmissionCommand(
        roomId = roomId,
        memberId = memberId,
        evaluations = listOf(
            QuestionEvaluation(1L, QuestionVote.MEMORABLE),
            QuestionEvaluation(2L, QuestionVote.DISAPPOINTING),
        ),
    )

    @Test
    fun `출석자가 자신의 면접 라운드에서 받은 원 질문을 모두 평가하면 한 번에 저장한다`() {
        givenNewSubmission()
        val response = slot<ClosingResponseEntity>()
        val savedResponse = mockk<ClosingResponseEntity> {
            every { roomId } returns this@ClosingSubmissionManagerTest.roomId
            every { memberId } returns this@ClosingSubmissionManagerTest.memberId
            every { createdAt } returns submittedAt
        }
        every { closingResponseRepository.saveAndFlush(capture(response)) } returns savedResponse

        val result = manager.submit(command)

        assertThat(result).isEqualTo(ClosingSubmission(roomId, memberId, submittedAt))
        assertThat(response.captured.questionVotes()).satisfiesExactlyInAnyOrder(
            {
                assertThat(it.questionId).isEqualTo(1L)
                assertThat(it.vote).isEqualTo(QuestionVote.MEMORABLE)
            },
            {
                assertThat(it.questionId).isEqualTo(2L)
                assertThat(it.vote).isEqualTo(QuestionVote.DISAPPOINTING)
            },
        )
        assertThat(response.captured.roomId).isEqualTo(roomId)
        assertThat(response.captured.memberId).isEqualTo(memberId)
        verify(exactly = 1) {
            closingQuestionRepository.findAllAskedTopLevelByRoomIdAndTargetMemberId(roomId, memberId)
        }
    }

    @Test
    fun `같은 사람이 다시 제출하면 첫 제출 시각과 평가를 유지한다`() {
        val firstSubmittedAt = submittedAt.minusMinutes(1)
        givenLockedRoom(RoomStatus.COMPLETED)
        every {
            closingResponseRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)
        } returns mockk<ClosingResponseEntity> {
            every { roomId } returns this@ClosingSubmissionManagerTest.roomId
            every { memberId } returns this@ClosingSubmissionManagerTest.memberId
            every { createdAt } returns firstSubmittedAt
        }

        val result = manager.submit(command)

        assertThat(result).isEqualTo(ClosingSubmission(roomId, memberId, firstSubmittedAt))
        verify(exactly = 0) {
            attendanceRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(any(), any())
            closingQuestionRepository.findAllAskedTopLevelByRoomIdAndTargetMemberId(any(), any())
            closingResponseRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `불참자가 제출하면 E1801 을 던지고 아무 응답도 저장하지 않는다`() {
        givenLockedRoom(RoomStatus.IN_PROGRESS)
        every {
            closingResponseRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)
        } returns null
        every {
            attendanceRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)
        } returns attendance(AttendanceStatus.ABSENT)

        assertClosingFails(CoreErrorType.CLOSING_SUBMISSION_FORBIDDEN)

        verify(exactly = 0) {
            closingQuestionRepository.findAllAskedTopLevelByRoomIdAndTargetMemberId(any(), any())
            closingResponseRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `진행 중인 룸이 아니면 E1802 를 던지고 질문을 읽지 않는다`() {
        givenLockedRoom(RoomStatus.CONFIRMED)
        every {
            closingResponseRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)
        } returns null

        assertClosingFails(CoreErrorType.CLOSING_NOT_AVAILABLE)

        verify(exactly = 0) {
            attendanceRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(any(), any())
            closingQuestionRepository.findAllAskedTopLevelByRoomIdAndTargetMemberId(any(), any())
        }
    }

    @Test
    fun `평가가 오늘 사용한 원 질문 집합과 다르면 E1803 을 던지고 일부만 저장하지 않는다`() {
        givenNewSubmission()
        val invalidCommands = listOf(
            command.copy(evaluations = command.evaluations.take(1)),
            command.copy(evaluations = command.evaluations + command.evaluations.first()),
            command.copy(
                evaluations = listOf(
                    QuestionEvaluation(1L, QuestionVote.MEMORABLE),
                    QuestionEvaluation(3L, QuestionVote.DISAPPOINTING),
                ),
            ),
        )

        invalidCommands.forEach { invalidCommand ->
            assertThatThrownBy { manager.submit(invalidCommand) }
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.CLOSING_QUESTION_MISMATCH)
                }
        }

        verify(exactly = 0) {
            closingResponseRepository.saveAndFlush(any())
        }
    }

    private fun givenNewSubmission() {
        givenLockedRoom(RoomStatus.IN_PROGRESS)
        every {
            closingResponseRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)
        } returns null
        every {
            attendanceRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)
        } returns attendance(AttendanceStatus.ATTENDED)
        every {
            closingQuestionRepository.findAllAskedTopLevelByRoomIdAndTargetMemberId(roomId, memberId)
        } returns listOf(
            question(1L),
            question(2L),
        )
    }

    private fun givenLockedRoom(status: RoomStatus) {
        every { roomRepository.findByIdForUpdate(roomId) } returns mockk<RoomEntity> {
            every { isActive() } returns true
            every { this@mockk.status } returns status
        }
    }

    private fun attendance(status: AttendanceStatus): AttendanceEntity = mockk {
        every { this@mockk.status } returns status
    }

    private fun question(id: Long): QuestionEntity = mockk {
        every { this@mockk.id } returns id
    }

    private fun assertClosingFails(errorType: CoreErrorType) {
        assertThatThrownBy { manager.submit(command) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
