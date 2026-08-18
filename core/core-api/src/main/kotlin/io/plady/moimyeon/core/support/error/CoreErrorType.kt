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
    ROOM_NOT_EDITABLE(HttpStatus.CONFLICT, ErrorCode.E1418, "모집 중인 룸만 수정할 수 있습니다.", LogLevel.WARN),

    // 방장 전용인 ROOM_FORBIDDEN(E1406)과 다르다 — 방장과 참여자 모두 통과한다.
    ROOM_PARTICIPANT_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        ErrorCode.E1419,
        "룸에 참여한 사람만 볼 수 있습니다.",
        LogLevel.WARN,
    ),

    // 모집 중이 아니라 못 접는 것(E1410)과 사유가 다르다. 화면이 안내를 갈라야 해서 코드도 가른다.
    ROOM_HAS_PARTICIPANTS(
        HttpStatus.CONFLICT,
        ErrorCode.E1420,
        "참여자가 있는 룸은 취소할 수 없습니다.",
        LogLevel.WARN,
    ),

    // 확정 거부 중 상태 계열(이미 확정·취소·완료·진행 중)은 E1410 을 그대로 쓴다. 화면이 새로고침하면
    // 정확한 상태를 다시 받으므로 코드를 넷으로 가를 실익이 없다. 아래 둘만 안내가 달라 가른다.
    ROOM_BELOW_MIN_CAPACITY(
        HttpStatus.CONFLICT,
        ErrorCode.E1421,
        "최소 진행 인원을 채워야 확정할 수 있습니다.",
        LogLevel.WARN,
    ),
    ROOM_SCHEDULE_PASSED_FOR_CONFIRMATION(
        HttpStatus.CONFLICT,
        ErrorCode.E1422,
        "진행 일정이 지나 확정할 수 없습니다.",
        LogLevel.WARN,
    ),

    // 확정은 "이 인원으로 진행한다"는 약속이라, 최소까지 내려온 뒤의 이탈은 그 약속을 깬다.
    // 확정 자체를 막는 E1421 과 반대 방향이라 코드를 가른다 — 안내 문구가 다르다.
    ROOM_AT_MIN_CAPACITY(
        HttpStatus.CONFLICT,
        ErrorCode.E1423,
        "최소 진행 인원이라 나갈 수 없습니다.",
        LogLevel.WARN,
    ),

    // 모집 중이 아니라는 것(E1410)과 다르다 — 확정된 룸에서는 나갈 수 있다.
    ROOM_ALREADY_CLOSED(
        HttpStatus.CONFLICT,
        ErrorCode.E1424,
        "이미 진행이 시작되었거나 끝난 룸입니다.",
        LogLevel.WARN,
    ),

    // 대기 신청 한도 초과(E1416)와 다른 축이다 — 이쪽은 이미 참여 중인 룸 수다(「룸 참여」 §4.1).
    // 안내 문구가 갈려야 신청자가 무엇을 정리해야 할지 안다(철회 vs 나가기).
    // 활성 룸 3개(E1427)와도 다르다 — 그쪽은 내가 만든 룸 수를 센다.
    PARTICIPATION_SLOT_EXCEEDED(
        HttpStatus.CONFLICT,
        ErrorCode.E1425,
        "참여 중인 룸이 3개라 새 룸에 참여할 수 없습니다.",
        LogLevel.WARN,
    ),

    // 같은 공고·직무로 이미 활성 룸을 셋 들고 있다(「룸 생성」 §4.7). 1~2개는 경고만 하고 통과시킨다.
    ACTIVE_ROOM_LIMIT_EXCEEDED(
        HttpStatus.CONFLICT,
        ErrorCode.E1427,
        "같은 공고와 직무로 만든 진행 예정인 룸이 3개라 더 만들 수 없습니다.",
        LogLevel.WARN,
    ),

    QUESTION_CARD_SET_NOT_OPEN(
        HttpStatus.CONFLICT,
        ErrorCode.E1501,
        "진행이 확정되었거나 완료된 방에서만 질문 카드셋을 조회할 수 있습니다.",
        LogLevel.WARN,
    ),
    QUESTION_CARD_SET_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        ErrorCode.E1502,
        "질문 카드셋에 접근할 권한이 없습니다.",
        LogLevel.WARN,
    ),
    QUESTION_CARD_SET_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        ErrorCode.E1503,
        "질문 카드셋을 찾을 수 없습니다.",
        LogLevel.WARN,
    ),
    QUESTION_PREPARATION_NOT_OPEN(
        HttpStatus.CONFLICT,
        ErrorCode.E1504,
        "진행이 확정된 방에서만 질문을 준비할 수 있습니다.",
        LogLevel.WARN,
    ),
    QUESTION_PREPARATION_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        ErrorCode.E1505,
        "질문을 작성하거나 삭제할 권한이 없습니다.",
        LogLevel.WARN,
    ),
    QUESTION_TARGET_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        ErrorCode.E1506,
        "질문을 남길 대상을 찾을 수 없습니다.",
        LogLevel.WARN,
    ),
    QUESTION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        ErrorCode.E1507,
        "질문을 찾을 수 없습니다.",
        LogLevel.WARN,
    ),
    QUESTION_HAS_OTHER_FOLLOW_UP(
        HttpStatus.CONFLICT,
        ErrorCode.E1508,
        "다른 참여자의 꼬리질문이 있는 원 질문은 삭제할 수 없습니다.",
        LogLevel.WARN,
    ),
    QUESTION_COMMENT_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        ErrorCode.E1509,
        "질문 댓글에 접근할 권한이 없습니다.",
        LogLevel.WARN,
    ),
    QUESTION_COMMENT_NOT_EDITABLE(
        HttpStatus.CONFLICT,
        ErrorCode.E1510,
        "진행 중인 룸에서만 질문 댓글을 작성하거나 변경할 수 있습니다.",
        LogLevel.WARN,
    ),
    QUESTION_COMMENT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        ErrorCode.E1511,
        "질문 댓글을 찾을 수 없습니다.",
        LogLevel.WARN,
    ),
    QUESTION_COMMENT_NOT_VIEWABLE(
        HttpStatus.CONFLICT,
        ErrorCode.E1512,
        "질문 댓글을 조회할 수 없는 룸 상태입니다.",
        LogLevel.WARN,
    ),

    INVALID_WEB_PUSH_REGISTRATION(
        HttpStatus.BAD_REQUEST,
        ErrorCode.E1601,
        "웹 푸시 등록 정보가 올바르지 않습니다.",
        LogLevel.WARN,
    ),

    ROOM_PROGRESS_NOT_STARTABLE(
        HttpStatus.CONFLICT,
        ErrorCode.E1701,
        "진행을 시작할 수 없는 룸입니다.",
        LogLevel.WARN,
    ),
    ROOM_PROGRESS_START_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        ErrorCode.E1702,
        "룸 진행을 시작할 권한이 없습니다.",
        LogLevel.WARN,
    ),
    ROOM_PROGRESS_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        ErrorCode.E1703,
        "룸 진행에 접근할 권한이 없습니다.",
        LogLevel.WARN,
    ),
    ROOM_PROGRESS_NOT_AVAILABLE(
        HttpStatus.CONFLICT,
        ErrorCode.E1704,
        "룸 진행 정보를 조회할 수 없는 상태입니다.",
        LogLevel.WARN,
    ),
    ROOM_PROGRESS_ATTENDANCE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        ErrorCode.E1705,
        "출석 기록을 찾을 수 없습니다.",
        LogLevel.WARN,
    ),
    ROOM_PROGRESS_PARTICIPANT_MISMATCH(
        HttpStatus.BAD_REQUEST,
        ErrorCode.E1706,
        "출석 대상이 확정 참여자와 일치하지 않습니다.",
        LogLevel.WARN,
    ),

    CLOSING_SUBMISSION_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        ErrorCode.E1801,
        "출석한 참여자만 클로징을 제출할 수 있습니다.",
        LogLevel.WARN,
    ),
    CLOSING_NOT_AVAILABLE(
        HttpStatus.CONFLICT,
        ErrorCode.E1802,
        "진행 중인 룸에서만 클로징을 제출할 수 있습니다.",
        LogLevel.WARN,
    ),
    CLOSING_QUESTION_MISMATCH(
        HttpStatus.BAD_REQUEST,
        ErrorCode.E1803,
        "평가 대상 질문이 오늘 사용한 질문과 일치하지 않습니다.",
        LogLevel.WARN,
    ),

    ROUND_FEEDBACK_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        ErrorCode.E1901,
        "이미 최종 피드백을 등록했습니다.",
        LogLevel.WARN,
    ),
    ROUND_FEEDBACK_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        ErrorCode.E1902,
        "라운드 피드백에 접근할 권한이 없습니다.",
        LogLevel.WARN,
    ),
    ROUND_FEEDBACK_NOT_EDITABLE(
        HttpStatus.CONFLICT,
        ErrorCode.E1903,
        "진행 중인 룸에서만 라운드 피드백을 작성하거나 수정할 수 있습니다.",
        LogLevel.WARN,
    ),
    ROUND_FEEDBACK_NOT_VIEWABLE(
        HttpStatus.CONFLICT,
        ErrorCode.E1904,
        "라운드 피드백을 조회할 수 없는 룸 상태입니다.",
        LogLevel.WARN,
    ),
    ROUND_FEEDBACK_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        ErrorCode.E1905,
        "최종 피드백 카드를 찾을 수 없습니다.",
        LogLevel.WARN,
    ),

    REVIEW_NOT_AVAILABLE(
        HttpStatus.CONFLICT,
        ErrorCode.E2001,
        "완료된 룸에서만 후기를 작성할 수 있습니다.",
        LogLevel.WARN,
    ),
    REVIEW_AUTHOR_NOT_ATTENDED(
        HttpStatus.FORBIDDEN,
        ErrorCode.E2002,
        "출석한 참여자만 후기를 작성할 수 있습니다.",
        LogLevel.WARN,
    ),
    REVIEW_TARGET_NOT_ATTENDED(
        HttpStatus.BAD_REQUEST,
        ErrorCode.E2003,
        "출석한 참여자에게만 후기를 작성할 수 있습니다.",
        LogLevel.WARN,
    ),
    REVIEW_SELF_NOT_ALLOWED(
        HttpStatus.BAD_REQUEST,
        ErrorCode.E2004,
        "자기 자신에게 후기를 작성할 수 없습니다.",
        LogLevel.WARN,
    ),
    REVIEW_DUPLICATED(
        HttpStatus.CONFLICT,
        ErrorCode.E2005,
        "이미 후기를 작성한 참여자입니다.",
        LogLevel.WARN,
    ),
    REVIEW_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        ErrorCode.E2006,
        "수정하거나 삭제할 후기를 찾을 수 없습니다.",
        LogLevel.WARN,
    ),
    REVIEW_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        ErrorCode.E2007,
        "후기를 작성한 회원만 수정하거나 삭제할 수 있습니다.",
        LogLevel.WARN,
    ),
    REVIEW_EDIT_WINDOW_CLOSED(
        HttpStatus.CONFLICT,
        ErrorCode.E2008,
        "공개 기준 시각이 지난 후기는 수정하거나 삭제할 수 없습니다.",
        LogLevel.WARN,
    ),

    ROOM_COMMENT_READ_ONLY(
        HttpStatus.CONFLICT,
        ErrorCode.E2101,
        "읽기 전용으로 전환된 방명록입니다. 지난 댓글은 볼 수 있지만 새로 남길 수는 없어요.",
        LogLevel.WARN,
    ),
    ROOM_COMMENT_NOT_MINE(
        HttpStatus.FORBIDDEN,
        ErrorCode.E2102,
        "내가 작성한 댓글만 삭제할 수 있습니다.",
        LogLevel.WARN,
    ),
    ROOM_COMMENT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        ErrorCode.E2103,
        "댓글을 찾을 수 없습니다.",
        LogLevel.WARN,
    ),
}
