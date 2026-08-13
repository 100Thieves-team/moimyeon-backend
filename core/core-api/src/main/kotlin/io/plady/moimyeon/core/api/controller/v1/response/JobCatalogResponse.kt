package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobGroup

data class JobCatalogResponse(
    val groups: List<JobGroupResponse>,
) {
    companion object {
        fun from(groups: List<JobGroup>): JobCatalogResponse {
            return JobCatalogResponse(
                groups.map { group ->
                    JobGroupResponse(
                        code = group.code,
                        displayName = group.displayName,
                        roles = group.roles.map(JobRoleResponse::from),
                    )
                },
            )
        }
    }
}

data class JobGroupResponse(
    val code: String,
    val displayName: String,
    val roles: List<JobRoleResponse>,
)
