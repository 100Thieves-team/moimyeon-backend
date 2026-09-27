package io.plady.moimyeon.core.domain.progress

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class RoomAutoCompleteConfigurationTest {
    private val contextRunner = ApplicationContextRunner().withInitializer(ConfigDataApplicationContextInitializer())

    @Test
    fun `배포 프로파일은 자동 완료를 켜고 스케줄 작업이 서로 막지 않게 스레드를 나눈다`() {
        listOf("local-dev", "dev", "staging", "live").forEach { profile ->
            contextRunner.withPropertyValues("spring.profiles.active=$profile").run { context ->
                assertThat(context.environment.getProperty("room.auto-complete.enabled", Boolean::class.java)).isTrue()
                assertThat(context.environment.getProperty("spring.task.scheduling.pool.size", Int::class.java)).isEqualTo(3)
            }
        }
    }

    @Test
    fun `local 에서는 자동 완료가 꺼져 있다`() {
        contextRunner.withPropertyValues("spring.profiles.active=local").run { context ->
            assertThat(context.environment.getProperty("room.auto-complete.enabled", Boolean::class.java)).isFalse()
        }
    }
}
