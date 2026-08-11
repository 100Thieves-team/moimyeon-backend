package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.domain.resume.ResumeSummary
import java.time.LocalDateTime
import java.util.UUID

// 참여자 명부 한 행(「룸 참여」 §4.5). 직무 "이름" 은 표시 관심사라 Facade 가 붙인다.
// 참여 상태는 담지 않는다 — 명부에 있다는 사실이 곧 참여 중(JOINED)이라는 뜻이다.
data class RoomParticipant(
    val memberId: UUID,
    val nickname: String,
    val isHost: Boolean,
    val joinedAt: LocalDateTime,
    // 방장은 아직 제출 행이 없어 비어 있을 수 있다(MOI-333).
    val resumeSummary: ResumeSummary?,
    val resumeSubmissionId: Long?,
    val canViewOriginal: Boolean,
)
