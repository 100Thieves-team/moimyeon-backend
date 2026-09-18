package io.plady.moimyeon.core.api.config

import io.plady.moimyeon.core.domain.resume.ResumeSummaryTimeSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()

    @Bean
    fun resumeSummaryTimeSource(): ResumeSummaryTimeSource = ResumeSummaryTimeSource(System::nanoTime)
}
