package io.plady.moimyeon.core.domain.profile

data class ProfileContent(
    val bio: String,
    val interestJobRoleIds: List<Long>,
    val interestCompanyIds: List<Long>,
)
