package io.plady.moimyeon

import io.plady.moimyeon.storage.redis.RedisNotificationStreamConsumer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@Import(RedisNotificationStreamConsumer::class)
@ConfigurationPropertiesScan(
    basePackages = [
        "io.plady.moimyeon.worker",
        "io.plady.moimyeon.storage.redis",
    ],
)
@SpringBootApplication(scanBasePackages = ["io.plady.moimyeon.worker"])
class WorkerApplication

fun main(args: Array<String>) {
    runApplication<WorkerApplication>(*args)
}
