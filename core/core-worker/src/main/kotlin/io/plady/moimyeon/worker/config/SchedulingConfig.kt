package io.plady.moimyeon.worker.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

@Profile("!test")
@EnableScheduling
@Configuration
class SchedulingConfig
