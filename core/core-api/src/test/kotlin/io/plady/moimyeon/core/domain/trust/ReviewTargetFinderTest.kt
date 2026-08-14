package io.plady.moimyeon.core.domain.trust

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.domain.progress.Attendance
import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ReviewTargetFinderTest {
    private val roomFinder = mockk<RoomFinder>()
    private val roomProgressReader = mockk<RoomProgressReader>()
    private val reviewRepository = mockk<ReviewRepository>()
    private val eligibilityValidator = ReviewEligibilityValidator(roomFinder, roomProgressReader)
    private val finder = ReviewTargetFinder(roomFinder, roomProgressReader, reviewRepository, eligibilityValidator)
    private val room = mockk<Room>()
    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `완료 룸 출석자 중 본인과 결석자를 제외하고 제출 상태를 표시한다`() {
        val submittedTargetId = UUID.randomUUID()
        val writableTargetId = UUID.randomUUID()
        val absentTargetId = UUID.randomUUID()
        val submittedReview = mockk<ReviewEntity> {
            every { targetMemberId } returns submittedTargetId
        }
        every { roomFinder.getRoom(roomId) } returns room
        every { room.status } returns RoomStatus.COMPLETED
        every { roomProgressReader.getAttendances(roomId) } returns listOf(
            Attendance(authorMemberId, AttendanceStatus.ATTENDED),
            Attendance(submittedTargetId, AttendanceStatus.ATTENDED),
            Attendance(writableTargetId, AttendanceStatus.ATTENDED),
            Attendance(absentTargetId, AttendanceStatus.ABSENT),
        )
        every {
            reviewRepository.findByRoomIdAndAuthorMemberIdAndDeletedAtIsNull(roomId, authorMemberId)
        } returns listOf(submittedReview)

        val targets = finder.getTargets(authorMemberId, roomId)

        assertThat(targets).containsExactly(
            ReviewTarget(submittedTargetId, ReviewTargetStatus.SUBMITTED),
            ReviewTarget(writableTargetId, ReviewTargetStatus.WRITABLE),
        )
    }

    @Test
    fun `완료되지 않은 룸이면 E2001 을 던진다`() {
        every { roomFinder.getRoom(roomId) } returns room
        every { room.status } returns RoomStatus.CANCELED

        assertThatThrownBy { finder.getTargets(authorMemberId, roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.REVIEW_NOT_AVAILABLE)
            }
    }

    @Test
    fun `작성자가 최신 출석 기록에서 결석이면 E2002 를 던진다`() {
        every { roomFinder.getRoom(roomId) } returns room
        every { room.status } returns RoomStatus.COMPLETED
        every { roomProgressReader.getAttendances(roomId) } returns listOf(
            Attendance(authorMemberId, AttendanceStatus.ABSENT),
        )

        assertThatThrownBy { finder.getTargets(authorMemberId, roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED)
            }
    }
}
