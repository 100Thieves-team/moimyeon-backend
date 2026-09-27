package io.plady.moimyeon.core.event

// 한 소비자라도 실패하면 모든 소비자에게 다시 넘기므로 같은 사실을 여러 번 받을 수 있다.
fun interface OutboxEventConsumer {
    fun consume(event: OutboxEvent)
}
