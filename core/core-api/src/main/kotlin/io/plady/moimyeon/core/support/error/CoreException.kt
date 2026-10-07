package io.plady.moimyeon.core.support.error

import io.plady.moimyeon.support.logging.SafeLogMessage

class CoreException(
    val errorType: CoreErrorType,
    val data: Any? = null,
) : RuntimeException(errorType.message),
    SafeLogMessage
