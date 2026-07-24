package io.plady.moimyeon.core.support.error

class CoreApiException(
    val errorType: CoreApiErrorType,
    val data: Any? = null,
) : RuntimeException(errorType.message)
