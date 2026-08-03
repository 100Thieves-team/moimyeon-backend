package io.plady.moimyeon.core.domain.resume

interface DocumentSummarizer {
    fun summarizePdf(content: ByteArray): String
}
