package io.plady.moimyeon.core.domain.trust

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Component
class ReviewPublicationJob(
    private val reviewPublicationRecorder: ReviewPublicationRecorder,
    private val clock: Clock,
) {
    @Scheduled(
        fixedDelayString = "\${review.publication.fixed-delay:1m}",
        initialDelayString = "\${review.publication.initial-delay:30s}",
    )
    fun run() {
        runCatching { reviewPublicationRecorder.recordPublishedReviews(LocalDateTime.now(clock)) }
            .onFailure { exception -> log.error(exception) { "review.publication.failed" } }
    }
}
