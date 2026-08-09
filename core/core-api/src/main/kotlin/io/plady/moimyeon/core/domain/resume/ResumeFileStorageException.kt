package io.plady.moimyeon.core.domain.resume

class ResumeFileStorageException(
    cause: Throwable,
) : RuntimeException("Resume file storage failed.", cause)
