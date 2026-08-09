package io.plady.moimyeon.core.domain.resume

class ResumeSummaryGenerationException(
    cause: Throwable? = null,
) : RuntimeException("Resume summary generation failed.", cause)
