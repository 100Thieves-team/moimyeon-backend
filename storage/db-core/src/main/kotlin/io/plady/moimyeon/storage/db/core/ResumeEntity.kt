package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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
    name: String,
    val fileKey: String,
    val originalName: String,
    val sizeBytes: Long,
    val contentType: String,
    summaryStatus: ResumeSummaryStatus,
    summaryContent: String? = null,
    isDefault: Boolean,
) : UuidBaseEntity(id) {
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    var summaryStatus: ResumeSummaryStatus = summaryStatus
        protected set

    var summaryContent: String? = summaryContent
        protected set

    var isDefault: Boolean = isDefault
        protected set

    fun makeDefault() {
        check(isActive()) { "삭제한 이력서는 기본으로 지정할 수 없습니다." }
        isDefault = true
    }

    fun releaseDefault() {
        isDefault = false
    }

    fun rename(name: String) {
        this.name = name
    }
}
