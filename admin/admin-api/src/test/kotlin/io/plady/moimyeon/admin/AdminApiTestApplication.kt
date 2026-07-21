package io.plady.moimyeon.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class AdminApiTestApplication

fun main(args: Array<String>) {
    runApplication<AdminApiTestApplication>(*args)
}
