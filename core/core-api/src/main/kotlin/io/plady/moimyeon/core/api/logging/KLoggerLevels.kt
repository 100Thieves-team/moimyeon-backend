package io.plady.moimyeon.core.api.logging

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.logging.LogLevel

// ErrorType의 logLevel로 예외를 기록한다. 어드바이스·비동기 핸들러가 같은 분기를 반복하지 않게 한 곳에 둔다.
fun KLogger.at(level: LogLevel, cause: Throwable, message: () -> String) {
    when (level) {
        LogLevel.ERROR, LogLevel.FATAL -> error(cause, message)
        LogLevel.WARN -> warn(cause, message)
        else -> info(cause, message)
    }
}
