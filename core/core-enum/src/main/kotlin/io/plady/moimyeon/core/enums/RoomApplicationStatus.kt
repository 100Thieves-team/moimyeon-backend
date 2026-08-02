package io.plady.moimyeon.core.enums

// 참가 신청의 처리 상태(「룸 참여」 §4.4·§6). 대기 신청만 방장이 수락·반려할 수 있고,
// 처리되면 대기에서 빠져 room_application.pending_member_id 가 NULL 로 풀린다.
enum class RoomApplicationStatus {
    PENDING, // 처리 대기 (방장 판단 전)
    ACCEPTED, // 방장 수락 → 참여자로 등록됨
    REJECTED, // 방장 반려 → 같은 룸 재신청 불가
    WITHDRAWN, // 신청자 철회 → 모집 중이면 재신청 가능
}
