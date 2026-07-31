package io.plady.moimyeon.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 목록에 없는 공고를 링크로 즉시 생성한다(「룸 생성」 §4.1). 승인 대기 없이 만들어져 바로 룸 생성에 사용할 수 있다(verified=false).
// 회사는 서비스가 관리하는 목록에서 선택한 companyId 로 받는다(§4.1, 신규 회사 생성은 범위 밖).
// 공고명은 링크 메타데이터(og:title)에서 제안된 값을 사용자가 확인·수정해 확정한 값이다.
data class CreateJobPostingRequest(
    val companyId: Long,
    @field:NotBlank
    @field:Size(max = 2000)
    val url: String,
    @field:NotBlank
    @field:Size(max = 100)
    val postingName: String,
)
