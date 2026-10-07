package io.plady.moimyeon.support.logging

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(LoggingProperties::class)
class LoggingAutoConfiguration {
    @Bean
    fun requestLogWriter(properties: LoggingProperties): RequestLogWriter = RequestLogWriter(properties)
}
