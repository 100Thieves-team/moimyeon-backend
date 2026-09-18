package io.plady.moimyeon.core.domain.closing

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.domain.progress.Attendance
import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.QuestionVote
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ClosingQuestionRepository
import io.plady.moimyeon.storage.db.core.ClosingResponseEntity
import io.plady.moimyeon.storage.db.core.ClosingResponseRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ClosingSubmissionManagerTest {
    private val roomRepository = mockk<RoomRepository>()
    private val roomProgressReader = mockk<RoomProgressReader>()
    private val closingQuestionRepository = mockk<ClosingQuestionRepository>()
    private val closingResponseRepository = mockk<ClosingResponseRepository>()
    private val roomStatusLogRepository = mockk<RoomStatusLogRepository>()
    private val manager = ClosingSubmissionManager(
        roomRepository,
        roomProgressReader,
        closingQuestionRepository,
        closingResponseRepository,
        roomStatusLogRepository,
    )

    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val otherMemberId = UUID.randomUUID()
    private lateinit var lockedRoom: RoomEntity
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
            roomProgressReader.isAttended(any(), any())
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
        every { roomProgressReader.isAttended(roomId, memberId) } returns false

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
            roomProgressReader.isAttended(any(), any())
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

    @Test
    fun `마지막 출석자가 제출하면 룸을 COMPLETED 로 전이하고 전이 로그를 남긴다`() {
        givenNewSubmission()
        givenAttendances(attended(memberId), attended(otherMemberId))
        givenSubmitters(otherMemberId, memberId)
        givenSavedResponse()
        val statusLog = slot<RoomStatusLogEntity>()
        every { roomStatusLogRepository.save(capture(statusLog)) } returnsArgument 0

        manager.submit(command)

        verify(exactly = 1) { lockedRoom.complete() }
        assertThat(statusLog.captured.roomId).isEqualTo(roomId)
        assertThat(statusLog.captured.transitionType).isEqualTo(RoomStatus.COMPLETED)
        assertThat(statusLog.captured.handlerMemberId).isEqualTo(memberId)
        assertThat(statusLog.captured.occurredAt).isEqualTo(submittedAt)
    }

    @Test
    fun `아직 제출하지 않은 출석자가 있으면 전이하지 않는다`() {
        givenNewSubmission()
        givenSavedResponse()

        manager.submit(command)

        verify(exactly = 0) {
            lockedRoom.complete()
            roomStatusLogRepository.save(any())
        }
    }

    @Test
    fun `불참자의 미제출은 전이를 막지 않는다`() {
        givenNewSubmission()
        givenAttendances(attended(memberId), absent(otherMemberId))
        givenSubmitters(memberId)
        givenSavedResponse()
        every { roomStatusLogRepository.save(any()) } returnsArgument 0

        manager.submit(command)

        verify(exactly = 1) { lockedRoom.complete() }
    }

    private fun givenNewSubmission() {
        givenLockedRoom(RoomStatus.IN_PROGRESS)
        every {
            closingResponseRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)
        } returns null
        every { roomProgressReader.isAttended(roomId, memberId) } returns true
        every {
            closingQuestionRepository.findAllAskedTopLevelByRoomIdAndTargetMemberId(roomId, memberId)
        } returns listOf(
            question(1L),
            question(2L),
        )
        // 판정은 새 저장 뒤마다 실행된다 — 기본값은 "아직 전원이 아님"으로 두고 전이 테스트가 덮어쓴다.
        givenAttendances(attended(memberId), attended(otherMemberId))
        givenSubmitters(memberId)
    }

    private fun givenLockedRoom(status: RoomStatus) {
        lockedRoom = mockk<RoomEntity> {
            every { isActive() } returns true
            every { this@mockk.status } returns status
            every { complete() } just runs
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns lockedRoom
    }

    private fun givenAttendances(vararg attendances: Attendance) {
        every { roomProgressReader.getAttendances(roomId) } returns attendances.toList()
    }

    private fun givenSubmitters(vararg memberIds: UUID) {
        every { closingResponseRepository.findAllByRoomIdAndDeletedAtIsNull(roomId) } returns
            memberIds.map { submitterId ->
                mockk<ClosingResponseEntity> {
                    every { this@mockk.memberId } returns submitterId
                }
            }
    }

    private fun givenSavedResponse() {
        every { closingResponseRepository.saveAndFlush(any()) } returns mockk<ClosingResponseEntity> {
            every { roomId } returns this@ClosingSubmissionManagerTest.roomId
            every { memberId } returns this@ClosingSubmissionManagerTest.memberId
            every { createdAt } returns submittedAt
        }
    }

    private fun attended(memberId: UUID): Attendance = Attendance(memberId, AttendanceStatus.ATTENDED)

    private fun absent(memberId: UUID): Attendance = Attendance(memberId, AttendanceStatus.ABSENT)

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
