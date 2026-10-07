package io.plady.moimyeon.core.domain.jobposting

// 링크 즉시 생성(「룸 생성」 §4.1)의 입력. companyId 는 카탈로그에서 고른 기존 회사,
// postingName 은 사용자가 OG 제안을 확인·수정해 확정한 값, url 은 출처로 저장된다.
data class JobPostingCreationCommand(
    val companyId: Long,
    val url: String,
    val postingName: String,
)
