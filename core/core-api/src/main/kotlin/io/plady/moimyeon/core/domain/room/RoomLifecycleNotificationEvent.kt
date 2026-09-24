package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.notification.NotificationEvent
import io.plady.moimyeon.core.notification.NotificationEventIdGenerator
import java.util.UUID

data class RoomLifecycleNotificationEvent(
    override val eventId: UUID = NotificationEventIdGenerator.generate(),
    override val eventType: EventType,
    val roomId: UUID,
    val recipientMemberId: UUID,
) : NotificationEvent
