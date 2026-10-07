package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.support.logging.SafeLogMessage

class ResumeFileStorageException(
    cause: Throwable,
) : RuntimeException("Resume file storage failed.", cause),
    SafeLogMessage
