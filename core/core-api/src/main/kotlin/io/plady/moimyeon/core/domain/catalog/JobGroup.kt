package io.plady.moimyeon.core.domain.catalog

data class JobGroup(
    val id: Long,
    val code: String,
    val displayName: String,
    val roles: List<JobRole>,
)
