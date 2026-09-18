package io.plady.moimyeon.core.domain.roomcomment

import java.time.LocalDateTime
import java.util.UUID

// 삭제된 글은 tombstone 으로 목록에 남는다(「룸 방명록」 §4.3) - 자리와 시각은 유지하고
// 작성자·내용은 persistence 경계에서부터 null 로 가려 밖으로 새지 않는다.
data class RoomComment(
    val id: Long,
    val authorMemberId: UUID?,
    val content: String?,
    val createdAt: LocalDateTime,
    val isDeleted: Boolean,
)
