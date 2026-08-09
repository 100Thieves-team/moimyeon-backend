package io.plady.moimyeon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class BatchApplication

fun main(args: Array<String>) {
    runApplication<BatchApplication>(*args)
}
