package io.plady.moimyeon.admin.support.error

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class AdminErrorType(val status: HttpStatus, val code: AdminErrorCode, val message: String, val logLevel: LogLevel) {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, AdminErrorCode.E500, "An unexpected error has occurred.", LogLevel.ERROR),
}
