package io.plady.moimyeon.core.domain.room

import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class RoomValidatorTest {
    private val roomRepository = mockk<RoomRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC)
    private val validator = RoomValidator(roomRepository, clock)

    private val roomId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 8, 4, 12, 0)

    @Test
    fun `모집 중이고 일정이 남은 룸이면 참가 신청을 받을 수 있다`() {
        every { roomRepository.findByIdForUpdate(roomId) } returns room(RoomStatus.RECRUITING, now.plusDays(1))

        assertThatCode { validator.validateAcceptingApplications(roomId) }.doesNotThrowAnyException()
    }

    @Test
    fun `진행 일정 판정 시각은 룸 잠금을 획득한 뒤 읽는다`() {
        val currentTime = mockk<Clock>()
        every { currentTime.instant() } returns Instant.parse("2026-08-04T12:00:00Z")
        every { currentTime.zone } returns ZoneOffset.UTC
        val lockAwareValidator = RoomValidator(roomRepository, currentTime)
        every { roomRepository.findByIdForUpdate(roomId) } returns room(RoomStatus.RECRUITING, now.plusDays(1))

        lockAwareValidator.validateAcceptingApplications(roomId)

        verifyOrder {
            roomRepository.findByIdForUpdate(roomId)
            currentTime.instant()
        }
    }

    @Test
    fun `존재하지 않는 룸이면 ROOM_NOT_FOUND 로 거부한다`() {
        every { roomRepository.findByIdForUpdate(roomId) } returns null

        assertValidationFails(CoreErrorType.ROOM_NOT_FOUND)
    }

    @Test
    fun `비활성 룸이면 ROOM_NOT_FOUND 로 거부한다`() {
        every {
            roomRepository.findByIdForUpdate(roomId)
        } returns room(RoomStatus.RECRUITING, now.plusDays(1), isActive = false)

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

    @Test
    fun `진행 시작 시각과 현재 시각이 같으면 ROOM_APPLICATION_SCHEDULE_PASSED 로 거부한다`() {
        every { roomRepository.findByIdForUpdate(roomId) } returns room(RoomStatus.RECRUITING, now)

        assertValidationFails(CoreErrorType.ROOM_APPLICATION_SCHEDULE_PASSED)
    }

    private fun assertValidationFails(errorType: CoreErrorType) {
        assertThatThrownBy { validator.validateAcceptingApplications(roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    private fun room(
        status: RoomStatus,
        startAt: LocalDateTime,
        isActive: Boolean = true,
    ): RoomEntity = mockk {
        every { isActive() } returns isActive
        every { this@mockk.status } returns status
        every { this@mockk.startAt } returns startAt
    }
}
