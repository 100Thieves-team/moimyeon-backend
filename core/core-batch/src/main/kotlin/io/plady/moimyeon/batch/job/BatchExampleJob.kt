package io.plady.moimyeon.batch.job

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BatchExampleJob {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *")
    fun run() {
        log.info("BatchExampleJob executed")
    }
}
