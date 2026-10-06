package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRoleSearchResult

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
