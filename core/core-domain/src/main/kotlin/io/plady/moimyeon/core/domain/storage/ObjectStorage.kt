package io.plady.moimyeon.core.domain.storage

interface ObjectStorage {
    fun store(key: String, contentType: String, content: ByteArray)

    fun read(key: String): ByteArray
}
