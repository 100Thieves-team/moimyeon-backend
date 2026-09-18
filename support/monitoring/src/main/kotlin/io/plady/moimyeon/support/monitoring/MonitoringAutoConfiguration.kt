package io.plady.moimyeon.support.monitoring

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import java.time.Clock

@AutoConfiguration
class MonitoringAutoConfiguration {
    @Bean
    fun telemetryHeartbeat(): MeterBinder = heartbeat(Clock.systemUTC())

    internal fun heartbeat(clock: Clock): MeterBinder = MeterBinder { registry ->
        // The value freezes if application export stops, even while Collector /metrics stays up.
        Gauge.builder("observability.heartbeat", clock) { it.instant().epochSecond.toDouble() }
            .baseUnit("seconds")
            .description("Unix time at application metric collection; not business readiness")
            .strongReference(true)
            .register(registry)
    }
}
