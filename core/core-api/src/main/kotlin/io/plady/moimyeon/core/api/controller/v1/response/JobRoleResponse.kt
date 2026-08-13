package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.catalog.JobRole

data class JobRoleResponse(
    val jobRoleId: Long,
    val code: String,
    val displayName: String,
) {
    companion object {
        fun from(jobRole: JobRole): JobRoleResponse {
            return JobRoleResponse(
                jobRoleId = jobRole.id,
                code = jobRole.code,
                displayName = jobRole.displayName,
            )
        }
    }
}
