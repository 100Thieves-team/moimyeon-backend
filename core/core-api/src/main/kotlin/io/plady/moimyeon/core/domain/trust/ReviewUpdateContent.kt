package io.plady.moimyeon.core.domain.trust

data class ReviewUpdateContent(
    val tags: Set<String>,
    val content: String,
)
