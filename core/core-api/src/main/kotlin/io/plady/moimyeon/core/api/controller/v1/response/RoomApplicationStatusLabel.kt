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
    }
}
