package io.plady.moimyeon.client.email

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class GmailSmtpEmailDeliveryProviderTest {
    private val mailSender = mockk<JavaMailSender>()
    private val provider = GmailSmtpEmailDeliveryProvider(
        mailSender = mailSender,
        fromAddress = "fallback@gmail.com",
    )

    @Test
    fun `Gmail SMTP 요청에 발신자 수신자 제목 본문을 전달한다`() {
        val request = slot<SimpleMailMessage>()
        every { mailSender.send(capture(request)) } just Runs

        provider.send(message())

        assertThat(request.captured.from).isEqualTo("fallback@gmail.com")
        assertThat(request.captured.to).containsExactly("member@example.com")
        assertThat(request.captured.subject).isEqualTo("제목")
        assertThat(request.captured.text).isEqualTo("본문")
    }

    @Test
    fun `Gmail SMTP 실패를 이메일 전송 실패로 변환한다`() {
        val cause = MailSendException("Gmail unavailable")
        every { mailSender.send(any<SimpleMailMessage>()) } throws cause

        assertThatThrownBy { provider.send(message()) }
            .isInstanceOf(EmailDeliveryException::class.java)
            .hasCause(cause)
    }

    private fun message() = EmailMessage(
        to = "member@example.com",
        subject = "제목",
        body = "본문",
    )
}
