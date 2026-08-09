package io.plady.moimyeon.core.domain.jobposting

// 목록에 공고명·회사를 표시하기 위한 최소 정보(MOI-383).
// 선택지로 고르는 JobPosting 과 달리 companyId 가 nullable 이다 — 크롤러가 회사를 유일매칭하지 못한
// 공고(company_id IS NULL)로 만들어진 룸이 이미 존재하고, 그 룸도 목록에는 나와야 하기 때문이다.
data class JobPostingLabel(
    val id: Long,
    val companyId: Long?,
    val postingName: String,
)
