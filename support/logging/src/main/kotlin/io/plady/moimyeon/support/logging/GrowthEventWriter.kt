package io.plady.moimyeon.support.logging

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

class GrowthEventWriter(
    private val logger: KLogger = KotlinLogging.logger {},
) {
    // 라우터는 INFO인 growth 사건만 분석 경로로 보낸다.
    fun write(entry: GrowthEventEntry) {
        logger.atInfo {
            message = GrowthEventEntry.MARKER
            payload = mapOf(GrowthEventEntry.PAYLOAD_KEY to entry)
        }
    }
}
