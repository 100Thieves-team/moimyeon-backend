package io.plady.moimyeon.core.domain.jobposting

// 통합 검색 결과 한 행. 회사별 목록의 JobPosting 과 달리 랭킹 개념을 갖는다.
data class JobPostingSearchItem(
    val id: Long,
    // 컬럼은 nullable 이지만 회사가 없는 공고는 조회 단계에서 제외된다.
    val companyId: Long,
    val postingName: String,
    val jobRoleId: Long?,
    val jobRoleName: String?,
    val sourceUrl: String?,
    val verified: Boolean,
    // 병합·정렬의 근거. 응답에는 싣지 않는다.
    val matchedByCompanyName: Boolean,
)
