package io.plady.moimyeon.support.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import kotlin.system.exitProcess

object LoggingBootstrapProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            SpringApplication(LoggingConfigurationTest.LoggingTestApplication::class.java).apply {
                setWebApplicationType(WebApplicationType.NONE)
                setBannerMode(Banner.Mode.OFF)
                setLogStartupInfo(false)
                setRegisterShutdownHook(false)
            }.run("--spring.config.location=classpath:logging.yml", "--spring.profiles.active=dev,live").close()
        } catch (_: IllegalArgumentException) {
            KotlinLogging.logger("io.plady.moimyeon.bootstrap.probe")
                .error(IllegalStateException("private@example.invalid")) { "private@example.invalid" }
            exitProcess(2)
        }
    }
}
