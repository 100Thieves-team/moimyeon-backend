package io.plady.moimyeon.core.domain.resume

interface ResumeSummaryGenerator {
    fun generate(content: ByteArray, deadline: ResumeSummaryDeadline): String
}
