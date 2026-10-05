package io.plady.moimyeon.core.event.payload

import java.util.UUID

// 참여자가 나가 확정 룸이 모집 중으로 돌아간 사실. participantMemberIds 는 방장을 포함한 남은 참여자다.
data class RoomRecruitingReopenedEventPayload(
    val roomId: UUID,
    val roomTitle: String,
    val hostMemberId: UUID,
    val participantMemberIds: List<UUID>,
) : EventPayload
