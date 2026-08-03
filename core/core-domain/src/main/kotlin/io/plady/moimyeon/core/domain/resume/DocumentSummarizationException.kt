package io.plady.moimyeon.core.domain.resume

class DocumentSummarizationException(
    cause: Throwable? = null,
) : RuntimeException("Document summarization failed.", cause)
