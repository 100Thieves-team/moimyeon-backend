package io.plady.moimyeon.core.notification

import io.plady.moimyeon.core.domain.room.RoomApplicationAcceptedEvent
import io.plady.moimyeon.core.enums.EventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationEventTest {
    @Test
    fun `참가 신청 수락 이벤트는 알림 이벤트 계약을 구현한다`() {
        val event: NotificationEvent = RoomApplicationAcceptedEvent(
            eventId = UUID.fromString("0198b4f4-2f00-7000-8000-000000000001"),
            applicationId = 1L,
            roomId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            applicantMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        )

        assertThat(event.eventType).isEqualTo(EventType.ROOM_APPLICATION_ACCEPTED)
    }

    @Test
    fun `알림 이벤트 식별자는 UUIDv7으로 생성한다`() {
        val event = RoomApplicationAcceptedEvent(
            applicationId = 1L,
            roomId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            applicantMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        )

        assertThat(event.eventId.version()).isEqualTo(7)
    }
}
