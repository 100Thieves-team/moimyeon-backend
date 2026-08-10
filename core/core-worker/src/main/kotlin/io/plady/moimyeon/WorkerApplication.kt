package io.plady.moimyeon

import io.plady.moimyeon.storage.redis.NotificationStreamMetrics
import io.plady.moimyeon.storage.redis.RedisNotificationStreamConsumer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@Import(RedisNotificationStreamConsumer::class, NotificationStreamMetrics::class)
@ConfigurationPropertiesScan(
    basePackages = [
        "io.plady.moimyeon.worker",
        "io.plady.moimyeon.storage.redis",
        "io.plady.moimyeon.client.email",
        "io.plady.moimyeon.client.webpush",
    ],
)
@SpringBootApplication(
    scanBasePackages = [
        "io.plady.moimyeon.worker",
        "io.plady.moimyeon.storage.db.core.config",
        "io.plady.moimyeon.client.email",
        "io.plady.moimyeon.client.webpush",
    ],
)
class WorkerApplication

fun main(args: Array<String>) {
    runApplication<WorkerApplication>(*args)
}
