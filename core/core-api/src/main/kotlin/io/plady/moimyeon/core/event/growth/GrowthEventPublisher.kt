package io.plady.moimyeon.core.event.growth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class GrowthEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun publish(event: GrowthEvent) {
        log.debug { "growth-event.publisher.publish code=${event.code} memberId=${event.memberId}" }
        applicationEventPublisher.publishEvent(event)
    }
}
