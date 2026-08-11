package io.plady.moimyeon.core.domain.notification

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.storage.db.core.WebPushRegistrationHash
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionEntity
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class WebPushSubscriptionManagerTest {
    private val repository = mockk<WebPushSubscriptionRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC)
    private val manager = WebPushSubscriptionManager(repository, clock)

    @Test
    fun `처음 업로드된 브라우저 등록을 회원에게 연결한다`() {
        every { repository.upsertRegistration(any(), any(), any(), any()) } returns 1
        every { repository.findByRegistrationHash(any()) } returns subscription(MEMBER_A)

        manager.register(MEMBER_A, REGISTRATION)

        verify(exactly = 1) {
            repository.upsertRegistration(
                memberId = MEMBER_A,
                registration = REGISTRATION.value,
                registrationHash = WebPushRegistrationHash.of(REGISTRATION.value),
                registeredAt = LocalDateTime.ofInstant(clock.instant(), clock.zone),
            )
        }
    }

    @Test
    fun `회원은 자신이 등록한 브라우저만 해지한다`() {
        val own = subscription(MEMBER_A)
        every { repository.findByRegistrationHash(any()) } returns own
        every { repository.delete(own) } returns Unit

        manager.unregister(MEMBER_A, REGISTRATION)

        verify(exactly = 1) { repository.delete(own) }
    }

    @Test
    fun `다른 회원의 브라우저 등록은 해지하지 않는다`() {
        val others = subscription(MEMBER_B)
        every { repository.findByRegistrationHash(any()) } returns others

        manager.unregister(MEMBER_A, REGISTRATION)

        verify(exactly = 0) { repository.delete(any()) }
    }

    private fun subscription(memberId: UUID) = WebPushSubscriptionEntity(
        memberId = memberId,
        registration = REGISTRATION.value,
        registrationHash = "hash",
        registeredAt = LocalDateTime.of(2026, 8, 1, 0, 0),
    )
}

private val MEMBER_A: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
private val MEMBER_B: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
private val REGISTRATION = WebPushRegistration("fcm-registration-id")
