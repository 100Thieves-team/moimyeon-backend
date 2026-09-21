package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.support.logging.SafeLogMessage

class ResumeSummaryGenerationException(
    cause: Throwable? = null,
) : RuntimeException("Resume summary generation failed.", cause),
    SafeLogMessage
