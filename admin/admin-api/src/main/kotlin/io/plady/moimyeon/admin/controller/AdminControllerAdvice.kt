package io.plady.moimyeon.admin.controller

import io.plady.moimyeon.admin.support.error.AdminErrorType
import io.plady.moimyeon.admin.support.error.AdminException
import io.plady.moimyeon.admin.support.response.AdminApiResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.logging.LogLevel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// core-api 런타임에 함께 조립되므로 admin 패키지의 컨트롤러에만 적용되도록 범위를 제한한다
@RestControllerAdvice(basePackages = ["io.plady.moimyeon.admin"])
class AdminControllerAdvice {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AdminException::class)
    fun handleAdminException(e: AdminException): ResponseEntity<AdminApiResponse<Any>> {
        when (e.errorType.logLevel) {
            LogLevel.ERROR -> log.error("AdminException : {}", e.message, e)
            LogLevel.WARN -> log.warn("AdminException : {}", e.message, e)
            else -> log.info("AdminException : {}", e.message, e)
        }
        return ResponseEntity(AdminApiResponse.error(e.errorType, e.data), e.errorType.status)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<AdminApiResponse<Any>> {
        log.error("Exception : {}", e.message, e)
        return ResponseEntity(AdminApiResponse.error(AdminErrorType.DEFAULT_ERROR), AdminErrorType.DEFAULT_ERROR.status)
    }
}
