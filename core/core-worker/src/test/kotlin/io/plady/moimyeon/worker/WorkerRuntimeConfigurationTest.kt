package io.plady.moimyeon.worker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class WorkerRuntimeConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=dev")

    @Test
    fun `Worker는 Flyway를 실행하지 않는다`() {
        contextRunner.run { context ->
            assertThat(context.environment.getProperty("spring.flyway.enabled", Boolean::class.java))
                .isFalse()
        }
    }

    @Test
    fun `Worker는 느린 MySQL 인증을 기다릴 수 있다`() {
        contextRunner.run { context ->
            assertThat(context.environment.getProperty("storage.datasource.core.connection-timeout", Long::class.java))
                .isEqualTo(10_000L)
        }
    }
}
