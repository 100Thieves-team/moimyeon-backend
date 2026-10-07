package io.plady.moimyeon.core.domain.jobposting

// 다른 개념이 공고를 참조로 들고 갈 때 필요한 최소 정보 — 공고명과 그 공고가 속한 회사.
// 선택지로 고르는 JobPosting 과 달리 companyId 가 nullable 이다: 크롤러가 회사를 유일매칭하지 못한
// 공고(company_id IS NULL)로 만들어진 룸이 이미 존재하고, 그 룸도 목록에는 나와야 하기 때문이다.
data class JobPostingRef(
    val id: Long,
    val companyId: Long?,
    val postingName: String,
)
