package io.plady.moimyeon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups

class CoreApiReadinessHealthGroupTest(
    private val healthEndpointGroups: HealthEndpointGroups,
) : ContextTest() {
    @Test
    fun `배포 준비 상태는 DB를 포함하고 알림 Redis를 제외한다`() {
        val readiness = requireNotNull(healthEndpointGroups.get("readiness"))

        assertThat(readiness.isMember("db")).isTrue()
        assertThat(readiness.isMember("redis")).isFalse()
    }
}
