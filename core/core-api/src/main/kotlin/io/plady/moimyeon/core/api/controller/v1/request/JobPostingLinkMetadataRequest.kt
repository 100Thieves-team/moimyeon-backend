package io.plady.moimyeon.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 목록에 없는 공고를 링크로 추가하기 전, 링크의 OG 태그를 읽어 공고명 후보와 미리보기를 돌려받는다(「룸 생성」 §4.1).
// 회사는 OG 에서 추출하지 않는다(회사명이 없거나 표기가 흔들리는 링크가 많아) — 회사는 생성(POST /v1/job-postings) 요청에서 지정한다.
data class JobPostingLinkMetadataRequest(
    @field:NotBlank
    @field:Size(max = 2000)
    val url: String,
)
