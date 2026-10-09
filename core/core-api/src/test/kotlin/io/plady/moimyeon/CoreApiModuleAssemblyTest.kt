package io.plady.moimyeon

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

// test 프로필은 Redis 어댑터 빈을 끄므로 컨텍스트 테스트로는 조립 누락을 잡지 못한다(MOI-591).
class CoreApiModuleAssemblyTest {
    @Test
    fun `API 런타임은 core-api 계약의 Redis 구현과 공유 Redis 설정을 함께 조립한다`() {
        listOf(
            "io.plady.moimyeon.storage.redis.RedisNotificationMessagePublisher",
            "io.plady.moimyeon.storage.redis.RedisOutboxRelayCoordinator",
            "io.plady.moimyeon.storage.redis.RedisAdminNotificationOperationsReader",
            "io.plady.moimyeon.storage.redis.RedisNotificationStreamProperties",
        ).forEach { className ->
            assertThatCode { Class.forName(className) }.doesNotThrowAnyException()
        }
    }
}
