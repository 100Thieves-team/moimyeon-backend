package io.plady.moimyeon.core.domain.trust

data class ReceivedReview(
    val id: Long,
    val tags: Set<String>,
    val content: String?,
)
