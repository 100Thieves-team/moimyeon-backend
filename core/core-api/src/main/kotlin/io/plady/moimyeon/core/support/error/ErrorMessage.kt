package io.plady.moimyeon.core.support.error

class ErrorMessage private constructor(
    val code: String,
    val message: String,
    val data: Any? = null,
) {
    constructor(errorType: CoreErrorType, data: Any? = null) : this(
        code = errorType.code.name,
        message = errorType.message,
        data = data,
    )

    constructor(errorType: CoreApiErrorType, data: Any? = null) : this(
        code = errorType.code.name,
        message = errorType.message,
        data = data,
    )
}
