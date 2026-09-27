package io.plady.moimyeon.storage.db.core

enum class OutboxRelayStatus {
    PENDING,
    PROCESSING,

    // 형식이 깨져 다시 시도해도 읽을 수 없는 행. 지우지 않고 원문을 남겨 사람이 확인한다.
    UNREADABLE,
}
