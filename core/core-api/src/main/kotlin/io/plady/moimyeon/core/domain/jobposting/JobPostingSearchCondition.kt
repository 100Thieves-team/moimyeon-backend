package io.plady.moimyeon.core.domain.jobposting

data class JobPostingSearchCondition(
    val matchedCompanyIds: List<Long>,
    // 정규화하지 않은 원본이다 — title 에는 정규화 컬럼이 없어 원본끼리 맞춰야 한다.
    val remainder: String,
    val tokens: List<String>,
)
