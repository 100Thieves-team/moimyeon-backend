package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

// 수정 기능이 없어 전 필드 val 이다(「룸 방명록」 §5 Out of Scope — 삭제 후 재작성으로 충분).
// 삭제는 베이스의 소프트 삭제이며, 삭제된 행도 목록에 tombstone 으로 남는다.
@Entity
@Table(name = "guestbook_post")
class GuestbookPostEntity(
    val roomGuestbookId: Long,
    @JdbcTypeCode(SqlTypes.BINARY)
    val authorMemberId: UUID,
    val content: String,
) : BaseEntity()
