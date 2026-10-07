package io.plady.moimyeon.storage.db.core

import java.time.LocalDateTime
import java.util.UUID

// 뒤이은 벌크 UPDATE 와 영속성 컨텍스트가 어긋나지 않게 엔티티 대신 값만 꺼낸다.
data class ReviewNotificationCandidate(
    val reviewId: Long,
    val roomId: UUID,
    val authorMemberId: UUID,
    val targetMemberId: UUID,
    val hiddenAt: LocalDateTime?,
)
