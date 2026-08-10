package io.plady.moimyeon.client.email

import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

internal class GmailSmtpEmailDeliveryProvider(
    private val mailSender: JavaMailSender,
    private val fromAddress: String,
) : EmailDeliveryProvider {
    override fun send(message: EmailMessage) {
        val request = SimpleMailMessage().apply {
            from = fromAddress
            setTo(message.to)
            subject = message.subject
            text = message.body
        }

        try {
            mailSender.send(request)
        } catch (exception: MailException) {
            throw EmailDeliveryException("Gmail SMTP 이메일 전송에 실패했습니다.", exception)
        }
    }
}
