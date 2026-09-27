package io.plady.moimyeon.core.event.payload

// sealed 라 payload 를 추가하면 NotificationComposer 의 빠진 분기를 컴파일러가 잡는다.
sealed interface EventPayload
