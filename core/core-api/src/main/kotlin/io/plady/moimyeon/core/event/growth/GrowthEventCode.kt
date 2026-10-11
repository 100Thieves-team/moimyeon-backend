package io.plady.moimyeon.core.event.growth

// 분석 도구로 보내는 서버 확정 사건(MOI-521 D3). 값은 `대상.과거형동사`이며 PostHog 사건 이름이 된다.
enum class GrowthEventCode(val value: String) {
    MEMBER_SIGNED_UP("member.signed_up"),
    MEMBER_WITHDREW("member.withdrew"),
    MEMBER_RESTORED("member.restored"),
    RESUME_REGISTERED("resume.registered"),
    ROOM_CREATED("room.created"),
    ROOM_CONFIRMED("room.confirmed"),
    ROOM_COMPLETED("room.completed"),
    ROOM_CANCELED("room.canceled"),
    ROOM_RECRUITING_REOPENED("room.recruiting_reopened"),
    ROOM_PARTICIPANT_LEFT("room_participant.left"),
    ROOM_APPLICATION_SUBMITTED("room_application.submitted"),
    ROOM_APPLICATION_WITHDRAWN("room_application.withdrawn"),
    ROOM_APPLICATION_ACCEPTED("room_application.accepted"),
    ROOM_APPLICATION_REJECTED("room_application.rejected"),
    REVIEW_SUBMITTED("review.submitted"),
    REVIEW_SKIPPED("review.skipped"),
}
