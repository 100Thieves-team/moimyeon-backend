package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRoleSearchResult

// 직무 검색 결과(「룸 생성」 §4.1). 공고와 독립한 평면 카탈로그에서 직무명으로 찾은 유효 직무 목록(상위 직군 포함).
data class JobRoleSearchResponse(
    val jobRoles: List<JobRoleSearchItemResponse>,
) {
    companion object {
        fun from(results: List<JobRoleSearchResult>): JobRoleSearchResponse {
            return JobRoleSearchResponse(
                results.map {
                    JobRoleSearchItemResponse(
                        jobRoleId = it.id,
                        code = it.code,
                        displayName = it.displayName,
                        group = JobRoleGroupResponse(it.groupCode, it.groupDisplayName),
                    )
                },
            )
        }
    }
}

data class JobRoleSearchItemResponse(
    val jobRoleId: Long,
    val code: String,
    val displayName: String,
    val group: JobRoleGroupResponse,
)

data class JobRoleGroupResponse(
    val code: String,
    val displayName: String,
)
