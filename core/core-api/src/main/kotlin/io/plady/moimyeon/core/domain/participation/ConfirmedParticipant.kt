package io.plady.moimyeon.core.domain.participation

import java.util.UUID

// 확정 후 나간 사람도 들어 있어 이력서 정보를 싣지 않는다. 나간 사람의 이력서 접근은 회수된다(「룸 참여」 R177).
data class ConfirmedParticipant(
    val memberId: UUID,
    val nickname: String?, // 탈퇴한 회원이면 비어 있다
)
