package io.plady.moimyeon.worker.notification.delivery

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.storage.db.core.WebPushRegistrationHash
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionEntity
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class DatabaseInvalidWebPushRegistrationRemoverTest {
    private val repository = mockk<WebPushSubscriptionRepository>(relaxed = true)
    private val remover = DatabaseInvalidWebPushRegistrationRemover(repository)

    @Test
    fun `FCM에서 만료된 등록 식별자와 정확히 일치하는 행만 삭제한다`() {
        val expired = subscription("expired-registration")
        val collision = subscription(
            registration = "different-registration",
            registrationHash = WebPushRegistrationHash.of("expired-registration"),
        )
        every {
            repository.findAllByRegistrationHashIn(
                setOf(WebPushRegistrationHash.of("expired-registration")),
            )
        } returns listOf(expired, collision)

        remover.remove(setOf("expired-registration"))

        verify(exactly = 1) { repository.deleteAll(listOf(expired)) }
    }

    private fun subscription(
        registration: String,
        registrationHash: String = WebPushRegistrationHash.of(registration),
    ) = WebPushSubscriptionEntity(
        memberId = UUID.randomUUID(),
        registration = registration,
        registrationHash = registrationHash,
        registeredAt = LocalDateTime.now(),
    )
}
