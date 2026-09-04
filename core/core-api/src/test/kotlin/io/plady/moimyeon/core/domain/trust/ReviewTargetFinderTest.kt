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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ReviewTargetFinderTest {
    private val roomFinder = mockk<RoomFinder>()
    private val roomProgressReader = mockk<RoomProgressReader>()
    private val eligibilityValidator = ReviewEligibilityValidator(roomFinder, roomProgressReader)
    private val finder = ReviewTargetFinder(roomFinder, roomProgressReader, eligibilityValidator)
    private val room = mockk<Room>()
    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `후기 대상은 완료 룸 출석자 중 본인과 결석자를 제외한 참여자다`() {
        val firstTargetId = UUID.randomUUID()
        val secondTargetId = UUID.randomUUID()
        val absentTargetId = UUID.randomUUID()
        every { roomFinder.getRoom(roomId) } returns room
        every { room.status } returns RoomStatus.COMPLETED
        every { roomProgressReader.getAttendances(roomId) } returns listOf(
            Attendance(authorMemberId, AttendanceStatus.ATTENDED),
            Attendance(firstTargetId, AttendanceStatus.ATTENDED),
            Attendance(secondTargetId, AttendanceStatus.ATTENDED),
            Attendance(absentTargetId, AttendanceStatus.ABSENT),
        )

        val targets = finder.getTargets(authorMemberId, roomId)

        assertThat(targets).containsExactly(
            ReviewTarget(firstTargetId),
            ReviewTarget(secondTargetId),
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
