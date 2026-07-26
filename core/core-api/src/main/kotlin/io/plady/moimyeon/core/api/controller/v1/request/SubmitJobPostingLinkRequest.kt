package io.plady.moimyeon.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// '찾는 공고가 없나요? 공고 링크를 남기면 추가해 드려요'(B·06, §4.1) — 운영이 공고를 추가할 수 있게 링크를 접수한다.
data class SubmitJobPostingLinkRequest(
    val companyId: Long? = null, // 회사 미선택 상태에서도 요청 가능하도록 nullable
    @field:NotBlank
    @field:Size(max = 2000)
    val url: String,
    @field:Size(max = 500)
    val memo: String? = null,
)
