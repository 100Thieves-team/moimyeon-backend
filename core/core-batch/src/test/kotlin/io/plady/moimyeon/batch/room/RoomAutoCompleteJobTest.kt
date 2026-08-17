package io.plady.moimyeon.batch.room

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class RoomAutoCompleteJobTest {
    private val completer = mockk<OverdueRoomCompleter>()
    private val job = RoomAutoCompleteJob(completer)

    @Test
    fun `한 룸의 실패가 다른 룸의 전이를 막지 않는다`() {
        val failing = UUID.randomUUID()
        val next = UUID.randomUUID()
        every { completer.findOverdueRoomIds(any()) } returns listOf(failing, next)
        every { completer.complete(failing, any()) } throws IllegalStateException("전이 실패")
        every { completer.complete(next, any()) } just runs

        job.run()

        verify(exactly = 1) { completer.complete(next, any()) }
    }
}
