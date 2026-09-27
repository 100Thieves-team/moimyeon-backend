package io.plady.moimyeon.core.event

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.event.payload.EventPayload
import io.plady.moimyeon.core.event.payload.ReviewPublishedEventPayload
import io.plady.moimyeon.core.event.payload.RoomApplicationAcceptedEventPayload
import io.plady.moimyeon.core.event.payload.RoomApplicationRejectedEventPayload
import io.plady.moimyeon.core.event.payload.RoomApplicationSubmittedEventPayload
import io.plady.moimyeon.core.event.payload.RoomCanceledEventPayload
import io.plady.moimyeon.core.event.payload.RoomCommentPostedEventPayload
import io.plady.moimyeon.core.event.payload.RoomCompletedEventPayload
import io.plady.moimyeon.core.event.payload.RoomConfirmedEventPayload
import io.plady.moimyeon.core.event.payload.RoomHostDelegatedEventPayload

// EventType 은 core-enum 에 있어 payload 클래스를 모르므로 짝을 여기서 짓는다.
val EventType.payloadClass: Class<out EventPayload>
    get() = when (this) {
        EventType.ROOM_APPLICATION_SUBMITTED -> RoomApplicationSubmittedEventPayload::class.java
        EventType.ROOM_APPLICATION_ACCEPTED -> RoomApplicationAcceptedEventPayload::class.java
        EventType.ROOM_APPLICATION_REJECTED -> RoomApplicationRejectedEventPayload::class.java
        EventType.ROOM_CONFIRMED -> RoomConfirmedEventPayload::class.java
        EventType.ROOM_COMPLETED -> RoomCompletedEventPayload::class.java
        EventType.ROOM_CANCELED -> RoomCanceledEventPayload::class.java
        EventType.ROOM_HOST_DELEGATED -> RoomHostDelegatedEventPayload::class.java
        EventType.REVIEW_PUBLISHED -> ReviewPublishedEventPayload::class.java
        EventType.ROOM_COMMENT_POSTED -> RoomCommentPostedEventPayload::class.java
    }
