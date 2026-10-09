package io.plady.moimyeon.worker.notification

import io.plady.moimyeon.storage.redis.NotificationStreamConsumer
import io.plady.moimyeon.worker.notification.delivery.ChannelNotificationSender
import io.plady.moimyeon.worker.notification.delivery.EmailSender
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipientFinder
import io.plady.moimyeon.worker.notification.delivery.NotificationSender
import io.plady.moimyeon.worker.notification.delivery.WebPushSender
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Path
import java.time.Clock

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
        @Value("\${notification.action-base-url}") actionBaseUrl: String,
    ): NotificationMessageHandler = NotificationMessageHandler(
        jsonMapper = jsonMapper,
        notificationSender = notificationSender,
        actionBaseUrl = actionBaseUrl,
    )

    @Bean
    fun workerHeartbeat(
        @Value("\${notification.worker.heartbeat-file}") heartbeatFile: String,
        clock: Clock,
    ): WorkerHeartbeat = FileWorkerHeartbeat(
        file = Path.of(heartbeatFile),
        clock = clock,
    )

    @Bean
    fun notificationMessageWorker(
        messageConsumer: NotificationStreamConsumer,
        messageHandler: NotificationMessageHandler,
        heartbeat: WorkerHeartbeat,
    ): NotificationMessageWorker = NotificationMessageWorker(
        messageConsumer = messageConsumer,
        messageHandler = messageHandler,
        heartbeat = heartbeat,
    )
}
