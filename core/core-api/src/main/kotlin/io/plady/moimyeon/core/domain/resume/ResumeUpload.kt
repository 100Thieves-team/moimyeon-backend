package io.plady.moimyeon.core.domain.resume

data class ResumeUpload(
    val originalName: String,
    val contentType: String,
    val content: ByteArray,
)
