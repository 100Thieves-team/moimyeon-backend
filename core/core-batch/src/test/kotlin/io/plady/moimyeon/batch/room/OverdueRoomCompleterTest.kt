package io.plady.moimyeon.batch.room

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoomStatusLogHandlerType
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class OverdueRoomCompleterTest {
    private val roomRepository = mockk<RoomRepository>()
    private val roomStatusLogRepository = mockk<RoomStatusLogRepository>()
    private val completer = OverdueRoomCompleter(roomRepository, roomStatusLogRepository)

    private val roomId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 8, 17, 12, 0)

    @Test
    fun `후보 조회 기준은 지금으로부터 8시간 전이다`() {
        val threshold = slot<LocalDateTime>()
        every { roomRepository.findInProgressStartedBefore(capture(threshold)) } returns emptyList()

        completer.findOverdueRoomIds(now)

        assertThat(threshold.captured).isEqualTo(now.minusHours(8))
    }

    @Test
    fun `진행 중인 룸을 COMPLETED 로 전이하고 SYSTEM 주체 로그를 남긴다`() {
        val room = lockedRoom(RoomStatus.IN_PROGRESS)
        val statusLog = slot<RoomStatusLogEntity>()
        every { roomStatusLogRepository.save(capture(statusLog)) } returnsArgument 0

        completer.complete(roomId, now)

        verify(exactly = 1) { room.complete() }
        assertThat(statusLog.captured.roomId).isEqualTo(roomId)
        assertThat(statusLog.captured.transitionType).isEqualTo(RoomStatus.COMPLETED)
        assertThat(statusLog.captured.handlerType).isEqualTo(RoomStatusLogHandlerType.SYSTEM)
        assertThat(statusLog.captured.handlerMemberId).isNull()
        assertThat(statusLog.captured.occurredAt).isEqualTo(now)
    }

    @Test
    fun `락을 잡은 뒤 진행 중이 아니게 된 룸은 건너뛴다`() {
        val room = lockedRoom(RoomStatus.COMPLETED)

        completer.complete(roomId, now)

        verify(exactly = 0) {
            room.complete()
            roomStatusLogRepository.save(any())
        }
    }

    @Test
    fun `락을 잡은 뒤 내려간 룸은 건너뛴다`() {
        every { roomRepository.findByIdForUpdate(roomId) } returns null

        completer.complete(roomId, now)

        verify(exactly = 0) { roomStatusLogRepository.save(any()) }
    }

    private fun lockedRoom(status: RoomStatus): RoomEntity {
        val room = mockk<RoomEntity> {
            every { isActive() } returns true
            every { this@mockk.status } returns status
            every { complete() } just runs
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        return room
    }
}
