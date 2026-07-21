package io.plady.moimyeon.admin.support.error

class AdminException(
    val errorType: AdminErrorType,
    val data: Any? = null,
) : RuntimeException(errorType.message)
