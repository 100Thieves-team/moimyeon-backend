package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.Optional
import java.util.UUID

class RoomProgressAccessValidatorTest {
    private val roomRepository = mockk<RoomRepository>()
    private val participationFinder = mockk<ParticipationFinder>()
    private val participationValidator = mockk<ParticipationValidator>()
    private val roomProgressAccessValidator = RoomProgressAccessValidator(roomRepository, participationFinder, participationValidator, Clock.systemUTC())
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()

    @Test
    fun `확정 룸 완료는 현재 방장만 통과한다`() {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { canComplete() } returns true
        }
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        justRun { participationValidator.validateHost(roomId, hostId) }

        roomProgressAccessValidator.validateCompleter(roomId, hostId)

        verify(exactly = 1) { participationValidator.validateHost(roomId, hostId) }
    }

    @Test
    fun `완료된 룸도 재요청 판정을 위해 방장이면 통과한다`() {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { canComplete() } returns false
            every { status } returns RoomStatus.COMPLETED
        }
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        justRun { participationValidator.validateHost(roomId, hostId) }

        roomProgressAccessValidator.validateCompleter(roomId, hostId)

        verify(exactly = 1) { participationValidator.validateHost(roomId, hostId) }
    }

    @Test
    fun `모집 중이거나 취소된 룸은 완료할 수 없다`() {
        listOf(RoomStatus.RECRUITING, RoomStatus.CANCELED).forEach { roomStatus ->
            val room = mockk<RoomEntity> {
                every { isActive() } returns true
                every { canComplete() } returns false
                every { status } returns roomStatus
            }
            every { roomRepository.findById(roomId) } returns Optional.of(room)

            assertThatThrownBy { roomProgressAccessValidator.validateCompleter(roomId, hostId) }
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_NOT_COMPLETABLE)
                }
        }
    }
}
