package io.plady.moimyeon.core.event.growth

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.analytics.AnalyticsIdGenerator
import io.plady.moimyeon.core.event.EventIdGenerator
import io.plady.moimyeon.support.logging.GrowthEventEntry
import io.plady.moimyeon.support.logging.GrowthEventWriter
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val log = KotlinLogging.logger {}

// 롤백된 사건을 전환으로 세지 않도록 커밋 후에만 기록한다. 커밋 직후 프로세스가 죽으면 빠질 수 있어
// 정확한 건수는 DB와 대조한다(MOI-411 DR-21). 트랜잭션 밖 발행은 저장이 이미 커밋된 뒤라 바로 기록한다.
@Component
class GrowthEventListener(
    private val analyticsIdGenerator: AnalyticsIdGenerator,
    private val growthEventWriter: GrowthEventWriter,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun record(event: GrowthEvent) {
        log.debug { "growth-event.listener.record code=${event.code} memberId=${event.memberId}" }
        // 이미 커밋된 요청을 분석 로그 실패로 실패시키지 않는다.
        runCatching {
            growthEventWriter.write(
                GrowthEventEntry(
                    eventCode = event.code.value,
                    eventId = EventIdGenerator.generate(),
                    analyticsId = analyticsIdGenerator.generate(event.memberId),
                    properties = event.properties,
                ),
            )
        }.onFailure { log.warn(it) { "growth-event.listener.failed code=${event.code}" } }
    }
}
