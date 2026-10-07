package io.plady.moimyeon.core.api.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.logging.at
import io.plady.moimyeon.core.support.error.CoreException
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import java.lang.reflect.Method

private val log = KotlinLogging.logger {}

class AsyncExceptionHandler : AsyncUncaughtExceptionHandler {
    override fun handleUncaughtException(e: Throwable, method: Method, vararg params: Any?) {
        if (e is CoreException) {
            log.at(e.errorType.logLevel, e) { "exception.async.core code=${e.errorType.code} message=${e.message} method=${method.name}" }
        } else {
            log.error(e) { "exception.async.unhandled type=${e.javaClass.name} method=${method.name}" }
        }
    }
}
