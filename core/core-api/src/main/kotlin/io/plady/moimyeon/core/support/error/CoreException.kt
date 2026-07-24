package io.plady.moimyeon.core.support.error

class CoreException(
    val errorType: CoreErrorType,
    val data: Any? = null,
) : RuntimeException(errorType.message)
