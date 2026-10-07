package io.plady.moimyeon.core.event

// 이전 버전 서버가 새 종류의 행을 집으면 난다. 새 버전 서버가 처리하도록 버리지 않는다.
class UnknownOutboxEventTypeException(
    type: String,
) : RuntimeException("알 수 없는 outbox 이벤트 종류입니다. type=$type")
