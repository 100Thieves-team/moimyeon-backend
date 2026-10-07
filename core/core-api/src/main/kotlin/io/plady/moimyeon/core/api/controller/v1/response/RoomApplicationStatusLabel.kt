package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.enums.RoomApplicationStatus

internal fun RoomApplicationStatus.toApplicantStatusLabel(): String {
    return when (this) {
        RoomApplicationStatus.PENDING -> "대기 중"
        RoomApplicationStatus.ACCEPTED -> "수락됨"
        RoomApplicationStatus.REJECTED -> "반려됨"
        RoomApplicationStatus.WITHDRAWN -> "철회함"
        RoomApplicationStatus.ROOM_CANCELED -> "룸이 취소됐어요"
        RoomApplicationStatus.ROOM_CONFIRMED -> "인원이 확정됐어요"
        // 반려가 아니라는 것이 이 문구의 일이다. 신청자 잘못이 아니고 재신청도 막히지 않는다.
        RoomApplicationStatus.SLOT_EXCEEDED -> "참여 중인 룸이 3개라 정리됐어요"
    }
}
