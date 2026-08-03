package io.plady.moimyeon.core.support.error

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class CoreErrorType(val status: HttpStatus, val code: ErrorCode, val message: String, val logLevel: LogLevel) {
    MEMBER_ALREADY_WITHDRAWN(HttpStatus.CONFLICT, ErrorCode.E1001, "이미 탈퇴한 회원입니다.", LogLevel.WARN),
    MEMBER_NOT_ACTIVE(HttpStatus.CONFLICT, ErrorCode.E1002, "활성 상태의 회원만 수행할 수 있는 작업입니다.", LogLevel.WARN),
    MEMBER_NOT_RESTRICTED(HttpStatus.CONFLICT, ErrorCode.E1003, "이용 제한 상태의 회원만 해제할 수 있습니다.", LogLevel.WARN),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, ErrorCode.E1004, "이미 연결된 소셜 계정입니다.", LogLevel.WARN),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, ErrorCode.E1005, "닉네임 형식이 올바르지 않습니다.", LogLevel.WARN),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E1006, "회원을 찾을 수 없습니다.", LogLevel.WARN),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, ErrorCode.E1007, "이미 사용 중인 닉네임입니다.", LogLevel.WARN),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E1009, "프로필을 찾을 수 없습니다.", LogLevel.WARN),
    RESUME_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E1010, "이력서를 찾을 수 없습니다.", LogLevel.WARN),
    RESUME_LIMIT_EXCEEDED(HttpStatus.CONFLICT, ErrorCode.E1011, "이력서는 최대 10개까지 등록할 수 있습니다.", LogLevel.WARN),
    RESUME_NOT_READY(HttpStatus.CONFLICT, ErrorCode.E1012, "AI 요약이 완료된 이력서만 사용할 수 있습니다.", LogLevel.WARN),
    RESUME_SUMMARY_NOT_RETRYABLE(HttpStatus.CONFLICT, ErrorCode.E1013, "실패한 AI 요약만 재시도할 수 있습니다.", LogLevel.WARN),
    INVALID_SESSION(HttpStatus.UNAUTHORIZED, ErrorCode.E1104, "세션이 유효하지 않습니다. 다시 로그인해주세요.", LogLevel.WARN),

    TERMS_NOT_AGREED(HttpStatus.CONFLICT, ErrorCode.E1201, "필수 약관에 동의해야 이용할 수 있습니다.", LogLevel.WARN),

    JOB_ROLE_NOT_FOUND(HttpStatus.BAD_REQUEST, ErrorCode.E1301, "존재하지 않는 직무입니다.", LogLevel.WARN),
    REGION_NOT_FOUND(HttpStatus.BAD_REQUEST, ErrorCode.E1302, "존재하지 않는 지역입니다.", LogLevel.WARN),
    COMPANY_NOT_FOUND(HttpStatus.BAD_REQUEST, ErrorCode.E1303, "존재하지 않는 회사입니다.", LogLevel.WARN),
}
