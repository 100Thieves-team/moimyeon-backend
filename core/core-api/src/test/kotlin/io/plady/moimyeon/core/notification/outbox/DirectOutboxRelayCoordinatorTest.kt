package io.plady.moimyeon.core.notification.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class DirectOutboxRelayCoordinatorTest {
    @Test
    fun `미처리 Outbox 재전달을 즉시 실행한다`() {
        val coordinator = DirectOutboxRelayCoordinator()
        var relayed = false

        val result = coordinator.relayPendingIfAvailable {
            relayed = true
        }

        assertThat(result).isTrue()
        assertThat(relayed).isTrue()
    }

    @Test
    fun `local 프로필에서는 직접 실행 조정자를 등록하지 않는다`() {
        coordinatorContext("local").use { context ->
            assertThat(context.getBeansOfType(DirectOutboxRelayCoordinator::class.java)).isEmpty()
        }
    }

    @Test
    fun `test 프로필에서는 직접 실행 조정자를 등록한다`() {
        coordinatorContext("local", "test").use { context ->
            assertThat(context.getBeansOfType(DirectOutboxRelayCoordinator::class.java)).hasSize(1)
        }
    }

    private fun coordinatorContext(vararg profiles: String): AnnotationConfigApplicationContext = AnnotationConfigApplicationContext().apply {
        environment.setActiveProfiles(*profiles)
        register(DirectOutboxRelayCoordinator::class.java)
        refresh()
    }
}
