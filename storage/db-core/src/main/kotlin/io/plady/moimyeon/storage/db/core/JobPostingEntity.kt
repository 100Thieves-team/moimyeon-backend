package io.plady.moimyeon.storage.db.core

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.util.UUID

// 내부 채용 공고 카탈로그(schema.sql job_posting). 크롤러·운영이 채우는 읽기 위주 데이터이며,
// 링크 즉시 생성(BE-03)만 앱이 verified=false 로 추가한다. 룸 생성은 이 공고를 통해 회사를 확정한다(PRD §4.1).
@Entity
@Table(
    name = "job_posting",
    uniqueConstraints = [UniqueConstraint(name = "uk_job_posting_source_uid", columnNames = ["source_uid"])],
)
class JobPostingEntity(
    val sourceUid: String,
    val companyId: Long?,
    val title: String,
    val isOpen: Boolean? = null,
    val sourceUrl: String? = null,
    val postedAt: LocalDateTime? = null,
    val verified: Boolean = false,
    val createdByMemberId: UUID? = null,
) : BaseEntity()
