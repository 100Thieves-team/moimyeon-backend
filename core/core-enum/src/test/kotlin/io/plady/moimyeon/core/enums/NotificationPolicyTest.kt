package io.plady.moimyeon.core.enums

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationPolicyTest {
    @Test
    fun `PUSH_ONLY는 웹 푸시 메시지 하나만 만든다`() {
        assertThat(NotificationPolicy.PUSH_ONLY.channels).containsExactly(NotificationChannel.WEB_PUSH)
        assertThat(NotificationPolicy.PUSH_ONLY.emailsWhenPushUndelivered).isFalse()
    }

    @Test
    fun `PUSH_ELSE_EMAIL은 웹 푸시 메시지 하나만 만들고 메일은 푸시 결과를 보고 보낸다`() {
        assertThat(NotificationPolicy.PUSH_ELSE_EMAIL.channels).containsExactly(NotificationChannel.WEB_PUSH)
        assertThat(NotificationPolicy.PUSH_ELSE_EMAIL.emailsWhenPushUndelivered).isTrue()
    }

    @Test
    fun `EMAIL_ONLY는 메일 메시지 하나만 만든다`() {
        assertThat(NotificationPolicy.EMAIL_ONLY.channels).containsExactly(NotificationChannel.EMAIL)
    }

    @Test
    fun `PUSH_AND_EMAIL은 웹 푸시와 메일 메시지를 따로 만든다`() {
        assertThat(NotificationPolicy.PUSH_AND_EMAIL.channels)
            .containsExactlyInAnyOrder(NotificationChannel.WEB_PUSH, NotificationChannel.EMAIL)
        assertThat(NotificationPolicy.PUSH_AND_EMAIL.emailsWhenPushUndelivered).isFalse()
    }
}
