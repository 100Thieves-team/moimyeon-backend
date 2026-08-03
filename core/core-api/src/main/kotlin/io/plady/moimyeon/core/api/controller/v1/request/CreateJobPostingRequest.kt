package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.jobposting.JobPostingCreationCommand
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

// 목록에 없는 공고를 링크로 즉시 생성한다(「룸 생성」 §4.1). 승인 대기 없이 만들어져 바로 룸 생성에 사용할 수 있다(verified=false).
// 회사는 서비스가 관리하는 목록에서 선택한 companyId 로 받는다(§4.1, 신규 회사 생성은 범위 밖).
// 공고명은 링크 메타데이터(og:title)에서 제안된 값을 사용자가 확인·수정해 확정한 값이다.
data class CreateJobPostingRequest(
    val companyId: Long,
    val url: String,
    val postingName: String,
) {
    // 형식 검증 후 생성 커맨드로 변환한다. companyId 양수·url(http/https)·postingName 규칙 위반은 400(E400).
    // companyId 의 실제 존재 여부는 도메인(CompanyValidator)이 검증한다.
    fun toCommand(): JobPostingCreationCommand {
        val trimmedUrl = url.trim()
        val trimmedName = postingName.trim()
        if (companyId <= 0) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        if (trimmedUrl.isBlank() || trimmedUrl.length > URL_MAX_LENGTH || !trimmedUrl.isHttpUrl()) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        if (trimmedName.isBlank() || trimmedName.length > POSTING_NAME_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return JobPostingCreationCommand(companyId = companyId, url = trimmedUrl, postingName = trimmedName)
    }

    companion object {
        private const val URL_MAX_LENGTH = 2000
        private const val POSTING_NAME_MAX_LENGTH = 100
    }
}
