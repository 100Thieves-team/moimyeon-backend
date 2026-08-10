package io.plady.moimyeon.client.email

import io.plady.moimyeon.worker.notification.delivery.EmailSender
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.mail.javamail.JavaMailSender
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client
import java.time.Duration

@Profile("local-dev", "dev", "staging", "live")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    SesEmailProperties::class,
    GmailEmailProperties::class,
)
internal class EmailClientConfiguration {
    @Bean
    fun sesV2Client(properties: SesEmailProperties): SesV2Client = SesV2Client.builder()
        .region(Region.of(properties.region))
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .apiCallTimeout(properties.apiCallTimeout)
                .apiCallAttemptTimeout(properties.apiCallAttemptTimeout)
                .build(),
        )
        .build()

    @Bean("sesEmailDeliveryProvider")
    fun sesEmailDeliveryProvider(
        sesV2Client: SesV2Client,
        properties: SesEmailProperties,
    ): EmailDeliveryProvider = SesEmailDeliveryProvider(
        sesClient = sesV2Client,
        fromAddress = properties.fromAddress,
    )

    @Bean("gmailEmailDeliveryProvider")
    fun gmailEmailDeliveryProvider(
        mailSender: JavaMailSender,
        properties: GmailEmailProperties,
    ): EmailDeliveryProvider = GmailSmtpEmailDeliveryProvider(
        mailSender = mailSender,
        fromAddress = properties.fromAddress,
    )

    @Bean
    fun emailSender(
        @Qualifier("sesEmailDeliveryProvider") primary: EmailDeliveryProvider,
        @Qualifier("gmailEmailDeliveryProvider") fallback: EmailDeliveryProvider,
    ): EmailSender = FailoverEmailSender(primary, fallback)
}

@ConfigurationProperties("notification.email.ses")
internal data class SesEmailProperties(
    val fromAddress: String,
    val region: String = "ap-northeast-2",
    val apiCallTimeout: Duration = Duration.ofSeconds(5),
    val apiCallAttemptTimeout: Duration = Duration.ofSeconds(3),
)

@ConfigurationProperties("notification.email.gmail")
internal data class GmailEmailProperties(
    val fromAddress: String,
)
