package io.plady.moimyeon.core.domain.jobposting

// 회사별 채용 공고(「룸 생성」 §4.1). 공고를 고르면 회사가 확정되고 직무 셀렉트가 자동으로 채워질 수 있다.
data class JobPosting(
    val id: Long,
    val companyId: Long,
    val postingName: String,
    // 대표 직무 힌트(선택). 매핑이 없으면 null.
    val jobRoleId: Long?,
    val jobRoleName: String?,
    val sourceUrl: String?,
    val verified: Boolean,
)
