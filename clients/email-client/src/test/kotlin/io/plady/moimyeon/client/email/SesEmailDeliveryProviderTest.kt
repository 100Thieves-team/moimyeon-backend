package io.plady.moimyeon.client.email

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse
import software.amazon.awssdk.services.sesv2.model.SesV2Exception

class SesEmailDeliveryProviderTest {
    private val sesClient = mockk<SesV2Client>()
    private val provider = SesEmailDeliveryProvider(
        sesClient = sesClient,
        fromAddress = "notification@moimyeon.com",
    )

    @Test
    fun `SES 요청에 발신자 수신자 제목 본문을 전달한다`() {
        val request = slot<SendEmailRequest>()
        every { sesClient.sendEmail(capture(request)) } returns SendEmailResponse.builder()
            .messageId("message-id")
            .build()

        provider.send(message())

        assertThat(request.captured.fromEmailAddress()).isEqualTo("notification@moimyeon.com")
        assertThat(request.captured.destination().toAddresses()).containsExactly("member@example.com")
        assertThat(request.captured.content().simple().subject().data()).isEqualTo("제목")
        assertThat(request.captured.content().simple().body().text().data()).isEqualTo("본문")
    }

    @Test
    fun `SES에 연결하지 못한 실패를 공급자 사용 불가로 변환한다`() {
        every { sesClient.sendEmail(any<SendEmailRequest>()) } throws SdkClientException.create("timeout")

        assertThatThrownBy { provider.send(message()) }
            .isInstanceOf(EmailProviderUnavailableException::class.java)
            .hasCauseInstanceOf(SdkClientException::class.java)
    }

    @Test
    fun `SES의 제한과 서버 오류를 공급자 사용 불가로 변환한다`() {
        listOf(429, 503).forEach { statusCode ->
            every { sesClient.sendEmail(any<SendEmailRequest>()) } throws SesV2Exception.builder()
                .statusCode(statusCode)
                .message("SES unavailable")
                .build()

            assertThatThrownBy { provider.send(message()) }
                .isInstanceOf(EmailProviderUnavailableException::class.java)
        }
    }

    @Test
    fun `SES의 잘못된 요청은 영구 실패로 변환한다`() {
        every { sesClient.sendEmail(any<SendEmailRequest>()) } throws SesV2Exception.builder()
            .statusCode(400)
            .message("invalid content")
            .build()

        assertThatThrownBy { provider.send(message()) }
            .isInstanceOf(PermanentEmailDeliveryException::class.java)
    }

    private fun message() = EmailMessage(
        to = "member@example.com",
        subject = "제목",
        body = "본문",
    )
}
