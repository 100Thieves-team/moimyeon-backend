package io.plady.moimyeon.core.domain.trust

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class ReviewEligibilityValidatorTest {
    private val roomFinder = mockk<RoomFinder>()
    private val roomProgressReader = mockk<RoomProgressReader>()
    private val validator = ReviewEligibilityValidator(roomFinder, roomProgressReader)
    private val room = mockk<Room>()
    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { roomFinder.getRoom(roomId) } returns room
        every { room.status } returns RoomStatus.COMPLETED
        every { roomProgressReader.findAttendance(roomId, authorMemberId) } returns
            Attendance(authorMemberId, AttendanceStatus.ATTENDED)
        every { roomProgressReader.findAttendance(roomId, targetMemberId) } returns
            Attendance(targetMemberId, AttendanceStatus.ATTENDED)
    }

    @Test
    fun `완료 룸에서 작성자와 대상자가 모두 출석하면 후기 행위를 허용한다`() {
        validator.validate(roomId, authorMemberId, targetMemberId)

        verify(exactly = 1) { roomProgressReader.findAttendance(roomId, authorMemberId) }
        verify(exactly = 1) { roomProgressReader.findAttendance(roomId, targetMemberId) }
    }

    @Test
    fun `완료되지 않은 룸이면 출석을 확인하지 않고 E2001 을 던진다`() {
        every { room.status } returns RoomStatus.CANCELED

        assertValidationFails(CoreErrorType.REVIEW_NOT_AVAILABLE)

        verify(exactly = 0) { roomProgressReader.findAttendance(any(), any()) }
    }

    @Test
    fun `작성자가 출석하지 않았으면 E2002 를 던진다`() {
        every { roomProgressReader.findAttendance(roomId, authorMemberId) } returns
            Attendance(authorMemberId, AttendanceStatus.ABSENT)

        assertValidationFails(CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED)

        verify(exactly = 0) { roomProgressReader.findAttendance(roomId, targetMemberId) }
    }

    @Test
    fun `작성자 출석 기록이 없어도 E2002 를 던진다`() {
        every { roomProgressReader.findAttendance(roomId, authorMemberId) } returns null

        assertValidationFails(CoreErrorType.REVIEW_AUTHOR_NOT_ATTENDED)
    }

    @Test
    fun `자기 자신이면 대상 출석을 다시 확인하지 않고 E2004 를 던진다`() {
        assertThatThrownBy { validator.validate(roomId, authorMemberId, authorMemberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.REVIEW_SELF_NOT_ALLOWED)
            }
        verify(exactly = 1) { roomProgressReader.findAttendance(roomId, authorMemberId) }
    }

    @Test
    fun `대상자가 출석하지 않았으면 E2003 을 던진다`() {
        every { roomProgressReader.findAttendance(roomId, targetMemberId) } returns
            Attendance(targetMemberId, AttendanceStatus.ABSENT)

        assertValidationFails(CoreErrorType.REVIEW_TARGET_NOT_ATTENDED)
    }

    private fun assertValidationFails(errorType: CoreErrorType) {
        assertThatThrownBy { validator.validate(roomId, authorMemberId, targetMemberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
