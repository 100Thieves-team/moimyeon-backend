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
    fun `배포 프로파일은 자동 종료와 알림을 두 스케줄러 스레드에서 실행한다`() {
        listOf("local-dev", "dev", "staging", "live").forEach { profile ->
            contextRunner.withPropertyValues("spring.profiles.active=$profile").run { context ->
                assertThat(context.environment.getProperty("room.auto-complete.enabled", Boolean::class.java)).isTrue()
                assertThat(context.environment.getProperty("spring.task.scheduling.pool.size", Int::class.java)).isEqualTo(2)
            }
        }
    }

    @Test
    fun `local에서는 자동 종료가 비활성이다`() {
        contextRunner.withPropertyValues("spring.profiles.active=local").run { context ->
            assertThat(context.environment.getProperty("room.auto-complete.enabled", Boolean::class.java)).isFalse()
        }
    }

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
