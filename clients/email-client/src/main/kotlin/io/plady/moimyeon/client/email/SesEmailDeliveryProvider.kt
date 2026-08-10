package io.plady.moimyeon.client.email

import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.Body
import software.amazon.awssdk.services.sesv2.model.Content
import software.amazon.awssdk.services.sesv2.model.Destination
import software.amazon.awssdk.services.sesv2.model.EmailContent
import software.amazon.awssdk.services.sesv2.model.Message
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest
import software.amazon.awssdk.services.sesv2.model.SesV2Exception

internal class SesEmailDeliveryProvider(
    private val sesClient: SesV2Client,
    private val fromAddress: String,
) : EmailDeliveryProvider {
    override fun send(message: EmailMessage) {
        val request = SendEmailRequest.builder()
            .fromEmailAddress(fromAddress)
            .destination(
                Destination.builder()
                    .toAddresses(message.to)
                    .build(),
            )
            .content(
                EmailContent.builder()
                    .simple(
                        Message.builder()
                            .subject(utf8Content(message.subject))
                            .body(
                                Body.builder()
                                    .text(utf8Content(message.body))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        try {
            sesClient.sendEmail(request)
        } catch (exception: SesV2Exception) {
            if (exception.statusCode() == TOO_MANY_REQUESTS || exception.statusCode() >= SERVER_ERROR) {
                throw EmailProviderUnavailableException("SES를 사용할 수 없습니다.", exception)
            }
            throw PermanentEmailDeliveryException("SES가 이메일 요청을 거절했습니다.", exception)
        } catch (exception: SdkClientException) {
            throw EmailProviderUnavailableException("SES에 연결할 수 없습니다.", exception)
        }
    }

    private fun utf8Content(value: String): Content = Content.builder()
        .charset(UTF_8)
        .data(value)
        .build()
}

private const val UTF_8 = "UTF-8"
private const val TOO_MANY_REQUESTS = 429
private const val SERVER_ERROR = 500
