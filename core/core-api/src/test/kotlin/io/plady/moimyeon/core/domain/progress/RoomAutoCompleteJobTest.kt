package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class RoomAutoCompleteJobTest {
    private val roomProgressManager = mockk<RoomProgressManager>()
    private val clock = Clock.fixed(Instant.parse("2026-09-18T03:00:00Z"), ZoneOffset.UTC)
    private val now = LocalDateTime.now(clock)
    private val job = RoomAutoCompleteJob(roomProgressManager, clock)

    @Test
    fun `한 룸의 실패가 다른 룸의 완료를 막지 않는다`() {
        val failing = UUID.randomUUID()
        val next = UUID.randomUUID()
        every { roomProgressManager.findOverdueRoomIds(any()) } returns listOf(failing, next)
        every { roomProgressManager.completeOverdue(failing, any()) } throws IllegalStateException("전이 실패")
        every { roomProgressManager.completeOverdue(next, any()) } returns true

        job.run()

        verify(exactly = 1) {
            roomProgressManager.findOverdueRoomIds(now)
            roomProgressManager.completeOverdue(next, now)
        }
    }
}
