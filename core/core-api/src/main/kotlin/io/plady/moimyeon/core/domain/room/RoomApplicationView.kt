package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.RoomApplicationStatus
import java.time.LocalDateTime
import java.util.UUID

// 방장용 참가 신청 목록의 한 행(「룸 참여」 §4.3). 신청자의 공개 정보와 신청 자체의 값만 담는다.
// 직무·활동 정보(trust 격벽)와 이력서 AI 요약(J5)은 아직 원천이 없어 담지 않고, 표현 계층이 null 로 채운다.
data class RoomApplicationView(
    val applicationId: Long,
    val applicantMemberId: UUID,
    val applicantNickname: String,
    val note: String?,
    val status: RoomApplicationStatus,
    val appliedAt: LocalDateTime,
)
