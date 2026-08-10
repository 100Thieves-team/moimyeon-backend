package io.plady.moimyeon.worker.notification

import io.plady.moimyeon.storage.redis.NotificationStreamConsumer
import io.plady.moimyeon.storage.redis.NotificationStreamHandlingResult
import io.plady.moimyeon.storage.redis.NotificationStreamMessage
import io.plady.moimyeon.worker.notification.delivery.EmailSender
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipient
import io.plady.moimyeon.worker.notification.delivery.NotificationRecipientFinder
import io.plady.moimyeon.worker.notification.delivery.WebPushSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

class NotificationWorkerConfigurationTest {
    private val contextRunner = baseContextRunner()

    private fun baseContextRunner(includeEmailSender: Boolean = true): ApplicationContextRunner {
        var runner = ApplicationContextRunner()
            .withUserConfiguration(NotificationWorkerConfiguration::class.java)
            .withBean(
                JsonMapper::class.java,
                { JsonMapper.builder().addModule(kotlinModule()).build() },
            )
            .withBean(
                NotificationStreamConsumer::class.java,
                { EmptyNotificationStreamConsumer() },
            )
            .withBean(
                NotificationRecipientFinder::class.java,
                {
                    NotificationRecipientFinder {
                        NotificationRecipient(
                            email = "recipient@example.com",
                            webPushRegistrations = setOf("registration"),
                        )
                    }
                },
            )
            .withBean(WebPushSender::class.java, { WebPushSender { _, _ -> } })
        if (includeEmailSender) {
            runner = runner.withBean(EmailSender::class.java, { EmailSender { _, _ -> } })
        }
        return runner
    }

    @Test
    fun `소비가 활성화되면 메시지 처리에 필요한 빈을 모두 조립한다`() {
        contextRunner
            .withPropertyValues("notification.worker.consumer.enabled=true")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(NotificationMessageWorker::class.java)
                assertThat(context).hasSingleBean(NotificationMessageHandler::class.java)
            }
    }

    @Test
    fun `소비가 비활성화되면 메시지 처리 빈을 만들지 않는다`() {
        contextRunner
            .withPropertyValues("notification.worker.consumer.enabled=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(NotificationMessageWorker::class.java)
                assertThat(context).doesNotHaveBean(NotificationMessageHandler::class.java)
            }
    }

    @Test
    fun `소비가 활성화됐지만 이메일 발송 구현이 없으면 시작에 실패한다`() {
        baseContextRunner(includeEmailSender = false)
            .withPropertyValues("notification.worker.consumer.enabled=true")
            .run { context ->
                assertThat(context).hasFailed()
            }
    }
}

private class EmptyNotificationStreamConsumer : NotificationStreamConsumer {
    override fun recoverPending(handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult): Int = 0

    override fun consumeNew(handler: (NotificationStreamMessage) -> NotificationStreamHandlingResult): Int = 0
}
