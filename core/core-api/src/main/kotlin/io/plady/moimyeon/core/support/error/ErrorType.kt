package io.plady.moimyeon.core.support.error

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class ErrorType(val status: HttpStatus, val code: ErrorCode, val message: String, val logLevel: LogLevel) {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "An unexpected error has occurred.", LogLevel.ERROR),

    MEMBER_ALREADY_WITHDRAWN(HttpStatus.CONFLICT, ErrorCode.E1001, "이미 탈퇴한 회원입니다.", LogLevel.WARN),
    MEMBER_NOT_ACTIVE(HttpStatus.CONFLICT, ErrorCode.E1002, "활성 상태의 회원만 수행할 수 있는 작업입니다.", LogLevel.WARN),
    MEMBER_NOT_RESTRICTED(HttpStatus.CONFLICT, ErrorCode.E1003, "이용 제한 상태의 회원만 해제할 수 있습니다.", LogLevel.WARN),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, ErrorCode.E1004, "이미 연결된 소셜 계정입니다.", LogLevel.WARN),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, ErrorCode.E1005, "닉네임 형식이 올바르지 않습니다.", LogLevel.WARN),
}
