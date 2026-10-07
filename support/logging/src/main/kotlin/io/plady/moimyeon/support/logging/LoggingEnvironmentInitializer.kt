package io.plady.moimyeon.support.logging

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered

class LoggingEnvironmentInitializer :
    ApplicationContextInitializer<ConfigurableApplicationContext>,
    Ordered {
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        // EnvironmentPrepared has configured safe logging; no application beans have started yet.
        val error = applicationContext.environment.getProperty("moimyeon.logging.validation-error", "")
        require(error.isEmpty()) { error }
    }
}
