package io.plady.moimyeon.worker.notification

import io.plady.moimyeon.storage.redis.NotificationStreamConsumer
import io.plady.moimyeon.worker.notification.delivery.ChannelNotificationSender
import io.plady.moimyeon.worker.notification.delivery.EmailSender
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipientFinder
import io.plady.moimyeon.worker.notification.delivery.NotificationSender
import io.plady.moimyeon.worker.notification.delivery.WebPushSender
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@ConditionalOnProperty(
    prefix = "notification.worker.consumer",
    name = ["enabled"],
    havingValue = "true",
)
@Configuration(proxyBeanMethods = false)
class NotificationWorkerConfiguration {
    @Bean
    fun notificationSender(
        recipientFinder: NotificationRecipientFinder,
        webPushSender: WebPushSender,
        emailSender: EmailSender,
    ): NotificationSender = ChannelNotificationSender(
        recipientFinder = recipientFinder,
        webPushSender = webPushSender,
        emailSender = emailSender,
    )

    @Bean
    fun notificationMessageHandler(
        jsonMapper: JsonMapper,
        notificationSender: NotificationSender,
    ): NotificationMessageHandler = NotificationMessageHandler(
        jsonMapper = jsonMapper,
        notificationSender = notificationSender,
    )

    @Bean
    fun notificationMessageWorker(
        messageConsumer: NotificationStreamConsumer,
        messageHandler: NotificationMessageHandler,
    ): NotificationMessageWorker = NotificationMessageWorker(
        messageConsumer = messageConsumer,
        messageHandler = messageHandler,
    )
}
