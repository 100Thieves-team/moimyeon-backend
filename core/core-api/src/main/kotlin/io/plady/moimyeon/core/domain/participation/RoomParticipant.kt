package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.domain.resume.ResumeSummary
import java.time.LocalDateTime
import java.util.UUID

// 참여자 명부 한 행(「룸 참여」 §4.5).
// 참여 상태는 담지 않는다 - 명부에 있다는 사실이 곧 참여 중(JOINED)이라는 뜻이다.
data class RoomParticipant(
    val memberId: UUID,
    // 탈퇴한 회원이면 비어 있다.
    val nickname: String?,
    val isHost: Boolean,
    val joinedAt: LocalDateTime,
    // 방장은 아직 제출 행이 없어 비어 있을 수 있다(MOI-333).
    val resumeSummary: ResumeSummary?,
    val resumeSubmissionId: Long?,
    val canViewOriginal: Boolean,
)
