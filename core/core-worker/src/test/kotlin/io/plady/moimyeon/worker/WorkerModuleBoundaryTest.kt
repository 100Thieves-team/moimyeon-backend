package io.plady.moimyeon.worker

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WorkerModuleBoundaryTest {
    @Test
    fun `워커 런타임은 core-api 애플리케이션을 포함하지 않는다`() {
        assertThatThrownBy {
            Class.forName("io.plady.moimyeon.CoreApiApplication")
        }.isInstanceOf(ClassNotFoundException::class.java)
    }

    // redis-core가 core-api·admin-api에 다시 의존하면 core-api만 바꾼 변경도 Worker 산출물을 바꾼다(MOI-591).
    @Test
    fun `워커 런타임은 admin-api와 core-api 계약의 Redis 구현을 포함하지 않는다`() {
        listOf(
            "io.plady.moimyeon.admin.notification.AdminNotificationOperationsReader",
            "io.plady.moimyeon.storage.redis.RedisNotificationMessagePublisher",
        ).forEach { className ->
            assertThatThrownBy {
                Class.forName(className)
            }.isInstanceOf(ClassNotFoundException::class.java)
        }
    }
}
