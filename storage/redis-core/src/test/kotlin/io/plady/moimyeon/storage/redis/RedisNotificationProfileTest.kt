package io.plady.moimyeon.storage.redis

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class RedisNotificationProfileTest {
    @Test
    fun `local 프로필에서는 Redis 재전달 조정자를 등록한다`() {
        coordinatorContext("local").use { context ->
            assertThat(context.getBeansOfType(RedisOutboxRelayCoordinator::class.java)).hasSize(1)
        }
    }

    @Test
    fun `test 프로필에서는 Redis 재전달 조정자를 등록하지 않는다`() {
        coordinatorContext("local", "test").use { context ->
            assertThat(context.getBeansOfType(RedisOutboxRelayCoordinator::class.java)).isEmpty()
        }
    }

    @Test
    fun `test가 다른 실행 프로필과 함께 활성화되어도 Redis 재전달 조정자를 등록하지 않는다`() {
        coordinatorContext("dev", "test").use { context ->
            assertThat(context.getBeansOfType(RedisOutboxRelayCoordinator::class.java)).isEmpty()
        }
    }

    private fun coordinatorContext(vararg profiles: String): AnnotationConfigApplicationContext = AnnotationConfigApplicationContext().apply {
        environment.setActiveProfiles(*profiles)
        beanFactory.registerSingleton("redisTemplate", mockk<StringRedisTemplate>())
        beanFactory.registerSingleton(
            "redisOutboxRelayProperties",
            RedisOutboxRelayProperties(
                lockKey = "notification-outbox-relay-test",
                lockDuration = Duration.ofSeconds(10),
            ),
        )
        register(RedisOutboxRelayCoordinator::class.java)
        refresh()
    }
}
