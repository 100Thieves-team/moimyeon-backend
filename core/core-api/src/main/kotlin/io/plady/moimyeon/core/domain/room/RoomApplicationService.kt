package io.plady.moimyeon.core.domain.room

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RoomApplicationService(
    private val roomApplicationManager: RoomApplicationManager,
) {
    // 참가 신청 수락(「룸 참여」 §4.4). 방장이 대기 신청을 받아들여 신청자를 참여자로 등록한다.
    fun accept(hostMemberId: UUID, roomId: UUID, applicationId: Long): ApplicationDecision = roomApplicationManager.accept(roomId, applicationId, hostMemberId)

    // 참가 신청 반려(§4.4). 사유는 선택이며 정원·참여자에는 영향이 없다.
    fun reject(hostMemberId: UUID, roomId: UUID, applicationId: Long, reason: RejectReason?): ApplicationDecision = roomApplicationManager.reject(roomId, applicationId, hostMemberId, reason)
}
