package io.plady.moimyeon.core.event.outbox

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("notification.outbox.relay")
data class PendingOutboxRelayProperties(
    val staleAfter: Duration = Duration.ofSeconds(10),
    val batchSize: Int = 100,
    val leaseDuration: Duration = Duration.ofMinutes(5),
)
