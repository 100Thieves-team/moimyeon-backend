package io.plady.moimyeon.core.domain.notification

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
class WebPushSubscriptionManagerIT(
    private val manager: WebPushSubscriptionManager,
    private val repository: WebPushSubscriptionRepository,
) : ContextTest() {
    @Test
    fun `같은 브라우저 등록은 한 행을 유지하며 마지막으로 업로드한 회원에게 연결된다`() {
        val registration = WebPushRegistration("integration-registration")

        manager.register(MEMBER_A, registration)
        manager.register(MEMBER_B, registration)

        assertThat(repository.count()).isEqualTo(1)
        assertThat(repository.findAllByMemberId(MEMBER_A)).isEmpty()
        assertThat(repository.findAllByMemberId(MEMBER_B))
            .singleElement()
            .extracting("registration")
            .isEqualTo(registration.value)
    }

    @Test
    fun `다른 회원의 해지는 무시하고 소유 회원의 해지만 물리 삭제한다`() {
        val registration = WebPushRegistration("integration-unregister-registration")
        manager.register(MEMBER_A, registration)

        manager.unregister(MEMBER_B, registration)
        assertThat(repository.count()).isEqualTo(1)

        manager.unregister(MEMBER_A, registration)
        assertThat(repository.count()).isZero()
    }
}

private val MEMBER_A: UUID = UUID.fromString("00000000-0000-0000-0000-000000000101")
private val MEMBER_B: UUID = UUID.fromString("00000000-0000-0000-0000-000000000102")
