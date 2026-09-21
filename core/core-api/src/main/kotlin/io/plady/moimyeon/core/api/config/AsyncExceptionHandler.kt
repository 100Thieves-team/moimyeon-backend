package io.plady.moimyeon.core.api.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.support.error.CoreException
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.boot.logging.LogLevel
import java.lang.reflect.Method

private val log = KotlinLogging.logger {}

class AsyncExceptionHandler : AsyncUncaughtExceptionHandler {
    override fun handleUncaughtException(e: Throwable, method: Method, vararg params: Any?) {
        if (e is CoreException) {
            when (e.errorType.logLevel) {
                LogLevel.ERROR -> log.error(e) { "CoreException code=${e.errorType.code} message=${e.message} method=${method.name}" }
                LogLevel.WARN -> log.warn(e) { "CoreException code=${e.errorType.code} message=${e.message} method=${method.name}" }
                else -> log.info(e) { "CoreException code=${e.errorType.code} message=${e.message} method=${method.name}" }
            }
        } else {
            // 프레임워크·드라이버 예외의 message는 사용자 데이터를 되풀이할 수 있어 타입만 남긴다. 스택은 예외 객체로 전달된다.
            log.error(e) { "Exception type=${e.javaClass.name} method=${method.name}" }
        }
    }
}
