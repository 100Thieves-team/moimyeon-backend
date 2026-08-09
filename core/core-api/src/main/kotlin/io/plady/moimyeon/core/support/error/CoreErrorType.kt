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
    JOB_POSTING_NOT_FOUND(HttpStatus.BAD_REQUEST, ErrorCode.E1304, "존재하지 않는 채용 공고입니다.", LogLevel.WARN),

    INVALID_ROOM_TITLE(HttpStatus.BAD_REQUEST, ErrorCode.E1401, "방 제목 형식이 올바르지 않습니다.", LogLevel.WARN),
    INVALID_ROOM_CAPACITY(HttpStatus.BAD_REQUEST, ErrorCode.E1402, "허용된 인원수 범위가 아닙니다.", LogLevel.WARN),
    INVALID_ROOM_SCHEDULE(HttpStatus.BAD_REQUEST, ErrorCode.E1403, "허용된 방 일정 범위가 아닙니다.", LogLevel.WARN),
    INVALID_ROOM_DESCRIPTION(HttpStatus.BAD_REQUEST, ErrorCode.E1404, "방 설명 형식이 올바르지 않습니다.", LogLevel.WARN),
    ROOM_START_AT_NOT_FUTURE(HttpStatus.BAD_REQUEST, ErrorCode.E1407, "방 시작 일정이 현재보다 과거입니다", LogLevel.WARN),

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E1405, "방을 찾을 수 없습니다.", LogLevel.WARN),
    ROOM_FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.E1406, "방장만 접근할 수 있습니다.", LogLevel.WARN),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E1408, "참가 신청을 찾을 수 없습니다.", LogLevel.WARN),
    APPLICATION_ALREADY_HANDLED(HttpStatus.CONFLICT, ErrorCode.E1409, "이미 처리된 신청입니다.", LogLevel.WARN),
    ROOM_NOT_RECRUITING(HttpStatus.CONFLICT, ErrorCode.E1410, "모집 중인 방이 아닙니다.", LogLevel.WARN),
    ROOM_CAPACITY_FULL(HttpStatus.CONFLICT, ErrorCode.E1411, "모집 정원이 가득 차 수락할 수 없습니다.", LogLevel.WARN),
    ROOM_APPLICATION_SCHEDULE_PASSED(HttpStatus.CONFLICT, ErrorCode.E1412, "이미 진행 일정이 지난 방에는 신청할 수 없습니다.", LogLevel.WARN),
    ROOM_APPLICATION_DUPLICATED(HttpStatus.CONFLICT, ErrorCode.E1413, "이미 신청했거나 참여 중인 방입니다.", LogLevel.WARN),
    ROOM_HOST_CANNOT_APPLY(HttpStatus.CONFLICT, ErrorCode.E1414, "방장은 자신의 방에 참가 신청할 수 없습니다.", LogLevel.WARN),
    ROOM_REAPPLICATION_NOT_ALLOWED(HttpStatus.CONFLICT, ErrorCode.E1415, "반려되거나 내보내진 방에는 다시 신청할 수 없습니다.", LogLevel.WARN),
    ROOM_APPLICATION_PENDING_LIMIT_EXCEEDED(
        HttpStatus.CONFLICT,
        ErrorCode.E1416,
        "동시에 대기할 수 있는 참가 신청은 최대 3건입니다.",
        LogLevel.WARN,
    ),
    ROOM_CAPACITY_BELOW_PARTICIPANTS(
        HttpStatus.CONFLICT,
        ErrorCode.E1417,
        "이미 참여 중인 인원보다 적게 모집 인원을 줄일 수 없습니다.",
        LogLevel.WARN,
    ),
}
