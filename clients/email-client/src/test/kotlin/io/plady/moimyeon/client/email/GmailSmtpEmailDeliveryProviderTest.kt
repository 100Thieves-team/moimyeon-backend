package io.plady.moimyeon.client.email

import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties

class GmailSmtpEmailDeliveryProviderTest {
    private val mailSender = mockk<JavaMailSender>()
    private val provider = GmailSmtpEmailDeliveryProvider(
        mailSender = mailSender,
        fromAddress = "fallback@gmail.com",
    )

    init {
        every { mailSender.createMimeMessage() } answers { JavaMailSenderImpl().createMimeMessage() }
    }

    @Test
    fun `Gmail SMTP 요청에 발신자 수신자 제목을 전달한다`() {
        val request = captureSentMessage()

        provider.send(message())

        assertThat(request.captured.from.map { it.toString() }).containsExactly("fallback@gmail.com")
        assertThat(request.captured.getRecipients(Message.RecipientType.TO).map { it.toString() })
            .containsExactly("member@example.com")
        assertThat(request.captured.subject).isEqualTo("제목")
    }

    @Test
    fun `Gmail SMTP 요청에 평문과 HTML 본문을 함께 싣는다`() {
        val request = captureSentMessage()

        provider.send(message())

        // 파트의 Content-Type 헤더는 saveChanges 때 기록된다. 실제 발송에서는
        // JavaMailSenderImpl 이 부르므로, 보내질 모습 그대로 읽는다.
        val content = request.captured.apply { saveChanges() }.content as MimeMultipart
        assertThat(content.contentType).startsWith("multipart/alternative")
        assertThat(content.count).isEqualTo(2)
        assertThat(content.getBodyPart(0).content).isEqualTo("본문")
        assertThat(content.getBodyPart(0).contentType).startsWith("text/plain")
        assertThat(content.getBodyPart(1).content).isEqualTo("<p>본문</p>")
        assertThat(content.getBodyPart(1).contentType).startsWith("text/html")
    }

    // 위 테스트가 읽는 값은 메모리에 있던 String 이라 charset·전송 인코딩이 틀려도 통과한다.
    // 한글이 깨지는지는 실제로 보낼 바이트로 왕복해야 드러난다.
    @Test
    fun `직렬화한 메일에서 한글 제목과 본문이 보존된다`() {
        val request = captureSentMessage()

        provider.send(message())

        val wire = ByteArrayOutputStream()
        request.captured.apply { saveChanges() }.writeTo(wire)
        val received = MimeMessage(Session.getInstance(Properties()), ByteArrayInputStream(wire.toByteArray()))

        assertThat(received.subject).isEqualTo("제목")
        val content = received.content as MimeMultipart
        assertThat(content.getBodyPart(0).content).isEqualTo("본문")
        assertThat(content.getBodyPart(1).content).isEqualTo("<p>본문</p>")
    }

    @Test
    fun `Gmail SMTP 실패를 이메일 전송 실패로 변환한다`() {
        val cause = MailSendException("Gmail unavailable")
        every { mailSender.send(any<MimeMessage>()) } throws cause

        assertThatThrownBy { provider.send(message()) }
            .isInstanceOf(EmailDeliveryException::class.java)
            .hasCause(cause)
    }

    @Test
    fun `수신자 주소를 읽을 수 없으면 영구 실패로 변환한다`() {
        every { mailSender.send(any<MimeMessage>()) } just Runs

        assertThatThrownBy { provider.send(message(to = "수신자 아님")) }
            .isInstanceOf(PermanentEmailDeliveryException::class.java)
            .hasCauseInstanceOf(MessagingException::class.java)
    }

    @Test
    fun `발신자 주소를 읽을 수 없으면 기동 시점에 실패한다`() {
        assertThatThrownBy {
            GmailSmtpEmailDeliveryProvider(mailSender = mailSender, fromAddress = "발신자 아님")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasCauseInstanceOf(AddressException::class.java)
    }

    private fun captureSentMessage(): CapturingSlot<MimeMessage> {
        val request = slot<MimeMessage>()
        every { mailSender.send(capture(request)) } just Runs
        return request
    }

    private fun message(to: String = "member@example.com") = EmailMessage(
        to = to,
        subject = "제목",
        htmlBody = "<p>본문</p>",
        textBody = "본문",
    )
}
