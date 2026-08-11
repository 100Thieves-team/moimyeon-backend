package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.notification.NotificationEvent
import io.plady.moimyeon.core.notification.NotificationEventIdGenerator
import java.util.UUID

data class RoomApplicationAcceptedEvent(
    override val eventId: UUID = NotificationEventIdGenerator.generate(),
    val applicationId: Long,
    val roomId: UUID,
    val applicantMemberId: UUID,
) : NotificationEvent {
    override val eventType: EventType = EventType.ROOM_APPLICATION_ACCEPTED
}
