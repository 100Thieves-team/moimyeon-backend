package io.plady.moimyeon.core.support.error

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class CoreApiErrorType(val status: HttpStatus, val code: ErrorCode, val message: String, val logLevel: LogLevel) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, ErrorCode.E400, "요청 형식이 올바르지 않습니다.", LogLevel.WARN),
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "An unexpected error has occurred.", LogLevel.ERROR),

    OAUTH_EMAIL_NOT_PROVIDED(HttpStatus.BAD_REQUEST, ErrorCode.E1101, "소셜 계정에서 이메일을 확인할 수 없습니다.", LogLevel.WARN),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, ErrorCode.E1102, "인증이 필요합니다.", LogLevel.WARN),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, ErrorCode.E1103, "접근 권한이 없습니다.", LogLevel.WARN),
}
