package io.plady.moimyeon.core.domain.participation

import java.util.UUID

// 참여 중인 회원의 최소 표현. 명부(RoomParticipant)와 달리 닉네임·이력서를 싣지 않는다 -
// "지금 참여 중인가, 방장인가"만 답하고 표시 조립은 호출부가 한다.
data class JoinedParticipant(
    val memberId: UUID,
    val isHost: Boolean,
)
