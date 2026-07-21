package io.plady.moimyeon.core.support.error

enum class ErrorCode {
    // 공통/HTTP 계열 (3자리 = HTTP 상태 대응)
    E500,

    // 회원 도메인 (4자리, E10xx)
    E1001, // 이미 탈퇴한 회원
    E1002, // 활성 상태의 회원이 아님
    E1003, // 이용 제한 상태의 회원이 아님
    E1004, // 이미 연결된 소셜 계정
    E1005, // 닉네임 형식 위반
}
