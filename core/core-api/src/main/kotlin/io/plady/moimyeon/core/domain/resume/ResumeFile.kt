package io.plady.moimyeon.core.domain.resume

data class ResumeFile(
    val key: String,
    val originalName: String,
    val sizeBytes: Long,
    val contentType: String,
)
