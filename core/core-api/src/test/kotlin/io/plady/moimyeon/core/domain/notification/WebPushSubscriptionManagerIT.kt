package io.plady.moimyeon.core.domain.notification

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
class WebPushSubscriptionManagerIT(
    private val webPushSubscriptionManager: WebPushSubscriptionManager,
    private val webPushSubscriptionRepository: WebPushSubscriptionRepository,
) : ContextTest() {
    @Test
    fun `같은 브라우저 등록은 한 행을 유지하며 마지막으로 업로드한 회원에게 연결된다`() {
        val registration = WebPushRegistration("integration-registration")

        webPushSubscriptionManager.register(MEMBER_A, registration)
        webPushSubscriptionManager.register(MEMBER_B, registration)

        assertThat(webPushSubscriptionRepository.count()).isEqualTo(1)
        assertThat(webPushSubscriptionRepository.findAllByMemberId(MEMBER_A)).isEmpty()
        assertThat(webPushSubscriptionRepository.findAllByMemberId(MEMBER_B))
            .singleElement()
            .extracting("registration")
            .isEqualTo(registration.value)
    }
}

private val MEMBER_A: UUID = UUID.fromString("00000000-0000-0000-0000-000000000101")
private val MEMBER_B: UUID = UUID.fromString("00000000-0000-0000-0000-000000000102")
