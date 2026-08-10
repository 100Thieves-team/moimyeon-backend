package io.plady.moimyeon.worker.notification.delivery

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionEntity
import io.plady.moimyeon.storage.db.core.WebPushSubscriptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class MemberNotificationRecipientFinderTest {
    private val memberRepository = mockk<MemberRepository>()
    private val webPushSubscriptionRepository = mockk<WebPushSubscriptionRepository>()
    private val finder = MemberNotificationRecipientFinder(memberRepository, webPushSubscriptionRepository)

    @Test
    fun `살아있는 회원의 이메일과 웹 푸시 등록을 알림 수신 정보로 반환한다`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val member = mockk<MemberEntity> {
            every { email } returns "member@example.com"
        }
        every { webPushSubscriptionRepository.findAllByMemberId(memberId) } returns listOf(
            subscription("registration-a"),
            subscription("registration-b"),
        )
        every { memberRepository.findByIdAndDeletedAtIsNull(memberId) } returns member

        val recipient = finder.find(memberId)

        assertThat(recipient.email).isEqualTo("member@example.com")
        assertThat(recipient.webPushRegistrations).containsExactly("registration-a", "registration-b")
        verify(exactly = 1) { memberRepository.findByIdAndDeletedAtIsNull(memberId) }
        verify(exactly = 1) { webPushSubscriptionRepository.findAllByMemberId(memberId) }
    }

    @Test
    fun `살아있는 회원을 찾지 못하면 수신 정보를 만들지 않는다`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        every { memberRepository.findByIdAndDeletedAtIsNull(memberId) } returns null

        assertThatThrownBy { finder.find(memberId) }
            .isInstanceOf(NotificationRecipientNotFoundException::class.java)
            .hasMessageContaining(memberId.toString())
        verify(exactly = 0) { webPushSubscriptionRepository.findAllByMemberId(any()) }
    }

    private fun subscription(registration: String) = mockk<WebPushSubscriptionEntity> {
        every { this@mockk.registration } returns registration
    }
}
