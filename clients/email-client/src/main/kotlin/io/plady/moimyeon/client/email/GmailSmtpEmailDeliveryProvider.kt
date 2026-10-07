package io.plady.moimyeon.client.email

import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMultipart
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender

internal class GmailSmtpEmailDeliveryProvider(
    private val mailSender: JavaMailSender,
    private val fromAddress: String,
) : EmailDeliveryProvider {
    init {
        // 발신자 주소는 설정값이므로 잘못되면 모든 메일이 실패한다. 기동 시점에 드러낸다.
        // 발송 때 터지면 SES 장애 중에만 보이고, 메시지별 영구 실패로 DLQ에 쌓인다.
        try {
            InternetAddress.parse(fromAddress, true)
        } catch (exception: AddressException) {
            throw IllegalArgumentException("Gmail 발신자 주소를 읽을 수 없습니다: notification.email.gmail.from-address", exception)
        }
    }

    override fun send(message: EmailMessage) {
        val request = try {
            compose(message)
        } catch (exception: MessagingException) {
            // 주소 파싱 실패 같은 조립 오류는 같은 입력에 반복되므로 재시도하지 않는다.
            // SES의 400 계열 거절을 영구 실패로 보내는 것과 같은 기준이다(modules.md).
            throw PermanentEmailDeliveryException("Gmail SMTP 메일을 조립하지 못했습니다.", exception)
        }

        try {
            mailSender.send(request)
        } catch (exception: MailException) {
            throw EmailDeliveryException("Gmail SMTP 이메일 전송에 실패했습니다.", exception)
        }
    }

    // MimeMessageHelper 는 첨부·인라인 이미지를 위해 mixed/related 를 한 겹 더 감싼다.
    // 같은 내용을 두 형식으로만 싣는 메일이라 multipart/alternative 를 직접 조립한다.
    private fun compose(message: EmailMessage) = mailSender.createMimeMessage().apply {
        setFrom(fromAddress)
        setRecipients(Message.RecipientType.TO, message.to)
        setSubject(message.subject, UTF_8)
        // alternative 는 뒤에 올수록 풍부한 형식이다. 평문을 먼저 싣는다.
        setContent(
            MimeMultipart(ALTERNATIVE).apply {
                addBodyPart(part(message.textBody, TEXT_PLAIN))
                addBodyPart(part(message.htmlBody, TEXT_HTML))
            },
        )
    }

    private fun part(
        content: String,
        mimeType: String,
    ) = MimeBodyPart().apply { setContent(content, "$mimeType; charset=$UTF_8") }
}

private const val ALTERNATIVE = "alternative"
private const val TEXT_PLAIN = "text/plain"
private const val TEXT_HTML = "text/html"
