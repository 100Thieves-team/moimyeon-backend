package io.plady.moimyeon.core.domain.resume

interface ResumeSummaryGenerator {
    fun generate(content: ByteArray): String
}
