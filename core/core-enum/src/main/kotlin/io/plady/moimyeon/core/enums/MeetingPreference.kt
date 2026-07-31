package io.plady.moimyeon.core.enums

enum class MeetingPreference {
    // 가입 시 빈 프로필이 함께 생기므로 "아직 안 고름"이 정상 상태다. null 대신 값으로 둔다.
    UNSPECIFIED,
    ONLINE,
    OFFLINE,
    BOTH,
}
