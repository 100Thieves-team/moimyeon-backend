package io.plady.moimyeon.core.domain.storage

class ObjectStorageException(
    cause: Throwable,
) : RuntimeException("Object storage operation failed.", cause)
