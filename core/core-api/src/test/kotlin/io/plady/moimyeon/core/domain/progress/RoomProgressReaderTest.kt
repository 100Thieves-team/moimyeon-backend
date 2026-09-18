package io.plady.moimyeon.core.domain.progress

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RoomProgressReaderTest {
    private val repository = mockk<AttendanceRepository>()
    private val reader = RoomProgressReader(repository)
    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

    @Test
    fun `출석 기록이 ATTENDED이면 출석 참여자다`() {
        val attendance = mockk<AttendanceEntity> {
            every { status } returns AttendanceStatus.ATTENDED
        }
        every { repository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId) } returns attendance

        assertThat(reader.isAttended(roomId, memberId)).isTrue()
    }

    @Test
    fun `출석 기록이 없으면 출석 참여자가 아니다`() {
        every { repository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId) } returns null

        assertThat(reader.isAttended(roomId, memberId)).isFalse()
    }
}
