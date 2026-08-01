package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "resume",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_resume_member_default", columnNames = ["_default_member_id"]),
    ],
)
class ResumeEntity(
    id: UUID,
    @JdbcTypeCode(SqlTypes.BINARY)
    val memberId: UUID,
    val name: String,
    val fileKey: String,
    val originalName: String,
    val sizeBytes: Long,
    val contentType: String,
    summaryStatus: ResumeSummaryStatus,
    summaryContent: String? = null,
    isDefault: Boolean,
    archivedAt: LocalDateTime? = null,
) : UuidBaseEntity(id) {
    @Enumerated(EnumType.STRING)
    var summaryStatus: ResumeSummaryStatus = summaryStatus
        protected set

    var summaryContent: String? = summaryContent
        protected set

    var isDefault: Boolean = isDefault
        protected set

    var archivedAt: LocalDateTime? = archivedAt
        protected set

    fun makeDefault() {
        check(archivedAt == null) { "숨긴 이력서는 기본으로 지정할 수 없습니다." }
        isDefault = true
    }

    fun releaseDefault() {
        isDefault = false
    }

    fun canHide(): Boolean = !isDefault && archivedAt == null

    fun hide(hiddenAt: LocalDateTime) {
        check(canHide()) { "기본 이력서나 이미 숨긴 이력서는 숨길 수 없습니다." }
        archivedAt = hiddenAt
    }
}
