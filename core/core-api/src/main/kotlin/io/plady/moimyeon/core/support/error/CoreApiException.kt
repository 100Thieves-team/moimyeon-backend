package io.plady.moimyeon.core.support.error

import io.plady.moimyeon.support.logging.SafeLogMessage

class CoreApiException(
    val errorType: CoreApiErrorType,
    val data: Any? = null,
) : RuntimeException(errorType.message),
    SafeLogMessage
