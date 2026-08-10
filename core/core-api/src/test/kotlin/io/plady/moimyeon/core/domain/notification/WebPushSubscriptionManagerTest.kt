package io.plady.moimyeon.core.domain.notification

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionEntity
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.assertj.core.api.Assertions.assertThat
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
        val saved = slot<WebPushSubscriptionEntity>()
        every { repository.findByRegistrationHash(any()) } returns null
        every { repository.save(capture(saved)) } answers { saved.captured }

        manager.register(MEMBER_A, REGISTRATION)

        assertThat(saved.captured.memberId).isEqualTo(MEMBER_A)
        assertThat(saved.captured.registration).isEqualTo(REGISTRATION.value)
        assertThat(saved.captured.registeredAt).isEqualTo(LocalDateTime.ofInstant(clock.instant(), clock.zone))
    }

    @Test
    fun `같은 등록을 다시 업로드하면 마지막 동기화 시각을 갱신한다`() {
        val existing = subscription(MEMBER_A)
        every { repository.findByRegistrationHash(any()) } returns existing

        manager.register(MEMBER_A, REGISTRATION)

        assertThat(existing.registeredAt).isEqualTo(LocalDateTime.ofInstant(clock.instant(), clock.zone))
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `다른 회원이 같은 브라우저 등록을 업로드하면 현재 회원에게 소유권을 이전한다`() {
        val existing = subscription(MEMBER_A)
        every { repository.findByRegistrationHash(any()) } returns existing

        manager.register(MEMBER_B, REGISTRATION)

        assertThat(existing.memberId).isEqualTo(MEMBER_B)
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
