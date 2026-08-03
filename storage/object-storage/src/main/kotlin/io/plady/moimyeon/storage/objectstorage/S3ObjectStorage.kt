package io.plady.moimyeon.storage.objectstorage

import io.plady.moimyeon.core.domain.storage.ObjectStorage
import io.plady.moimyeon.core.domain.storage.ObjectStorageException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@Profile("local-dev", "dev", "staging", "live")
@Component
class S3ObjectStorage(
    private val s3Client: S3Client,
    private val properties: S3ObjectStorageProperties,
) : ObjectStorage {
    override fun store(key: String, contentType: String, content: ByteArray) {
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(content.size.toLong())
            .build()

        try {
            s3Client.putObject(request, RequestBody.fromBytes(content))
        } catch (exception: SdkException) {
            throw ObjectStorageException(exception)
        }
    }

    override fun read(key: String): ByteArray {
        val request = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .build()
        return try {
            s3Client.getObjectAsBytes(request).asByteArray()
        } catch (exception: SdkException) {
            throw ObjectStorageException(exception)
        }
    }
}
