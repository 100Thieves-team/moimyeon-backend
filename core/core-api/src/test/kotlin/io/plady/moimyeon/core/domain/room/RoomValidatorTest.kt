package io.plady.moimyeon.core.domain.room

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RoomValidatorTest {
    private val roomRepository = mockk<RoomRepository>()
    private val validator = RoomValidator(roomRepository)

    private val roomId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 8, 4, 12, 0)

    @Test
    fun `모집 중이고 일정이 남은 룸이면 참가 신청을 받을 수 있다`() {
        every { roomRepository.findByIdForUpdate(roomId) } returns room(RoomStatus.RECRUITING, now.plusDays(1))

        assertThatCode { validator.validateAcceptingApplications(roomId, now) }.doesNotThrowAnyException()
    }

    @Test
    fun `존재하지 않는 룸이면 ROOM_NOT_FOUND 로 거부한다`() {
        every { roomRepository.findByIdForUpdate(roomId) } returns null

        assertValidationFails(CoreErrorType.ROOM_NOT_FOUND)
    }

    @Test
    fun `모집 중인 룸이 아니면 ROOM_NOT_RECRUITING 으로 거부한다`() {
        every { roomRepository.findByIdForUpdate(roomId) } returns room(RoomStatus.CONFIRMED, now.plusDays(1))

        assertValidationFails(CoreErrorType.ROOM_NOT_RECRUITING)
    }

    @Test
    fun `진행 일정이 지났으면 ROOM_APPLICATION_SCHEDULE_PASSED 로 거부한다`() {
        every { roomRepository.findByIdForUpdate(roomId) } returns room(RoomStatus.RECRUITING, now.minusMinutes(1))

        assertValidationFails(CoreErrorType.ROOM_APPLICATION_SCHEDULE_PASSED)
    }

    private fun assertValidationFails(errorType: CoreErrorType) {
        assertThatThrownBy { validator.validateAcceptingApplications(roomId, now) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    private fun room(status: RoomStatus, startAt: LocalDateTime): RoomEntity = mockk {
        every { isActive() } returns true
        every { this@mockk.status } returns status
        every { this@mockk.startAt } returns startAt
    }
}
