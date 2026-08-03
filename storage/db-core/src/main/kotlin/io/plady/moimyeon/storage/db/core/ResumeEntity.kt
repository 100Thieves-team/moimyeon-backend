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
    name: String,
    val fileKey: String,
    val originalName: String,
    val sizeBytes: Long,
    val contentType: String,
    summaryStatus: ResumeSummaryStatus,
    summaryContent: String? = null,
    summaryStartedAt: LocalDateTime,
    isDefault: Boolean,
) : UuidBaseEntity(id) {
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    var summaryStatus: ResumeSummaryStatus = summaryStatus
        protected set

    var summaryContent: String? = summaryContent
        protected set

    var summaryStartedAt: LocalDateTime = summaryStartedAt
        protected set

    var isDefault: Boolean = isDefault
        protected set

    fun makeDefault() {
        check(isActive()) { "삭제한 이력서는 기본으로 지정할 수 없습니다." }
        check(summaryStatus == ResumeSummaryStatus.DONE) { "요약이 완료된 이력서만 기본으로 지정할 수 있습니다." }
        isDefault = true
    }

    fun releaseDefault() {
        isDefault = false
    }

    fun rename(name: String) {
        this.name = name
    }

    fun completeSummary(content: String) {
        check(summaryStatus == ResumeSummaryStatus.PROCESSING) { "처리 중인 이력서 요약만 완료할 수 있습니다." }
        require(content.isNotBlank()) { "완료된 이력서 요약은 비어 있을 수 없습니다." }
        summaryStatus = ResumeSummaryStatus.DONE
        summaryContent = content
    }

    fun failSummary() {
        if (summaryStatus == ResumeSummaryStatus.FAILED) return
        check(summaryStatus == ResumeSummaryStatus.PROCESSING) { "처리 중인 이력서 요약만 실패로 변경할 수 있습니다." }
        summaryStatus = ResumeSummaryStatus.FAILED
        summaryContent = null
    }

    fun retrySummary(startedAt: LocalDateTime) {
        check(summaryStatus == ResumeSummaryStatus.FAILED) { "실패한 이력서 요약만 재시도할 수 있습니다." }
        summaryStatus = ResumeSummaryStatus.PROCESSING
        summaryContent = null
        summaryStartedAt = startedAt
    }
}
