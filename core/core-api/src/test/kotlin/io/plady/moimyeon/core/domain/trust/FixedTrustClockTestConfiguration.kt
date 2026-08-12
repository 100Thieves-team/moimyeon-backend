package io.plady.moimyeon.core.domain.trust

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

val TRUST_NOW: LocalDateTime = LocalDateTime.of(2026, 8, 12, 12, 0)

@TestConfiguration(proxyBeanMethods = false)
class FixedTrustClockTestConfiguration {
    @Bean
    @Primary
    fun fixedTrustClock(): Clock = Clock.fixed(TRUST_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
}
