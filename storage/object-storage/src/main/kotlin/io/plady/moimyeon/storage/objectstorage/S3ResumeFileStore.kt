package io.plady.moimyeon.storage.objectstorage

import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeFileStorageException
import io.plady.moimyeon.core.domain.resume.ResumeFileStore
import io.plady.moimyeon.core.domain.resume.ResumeUpload
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration
import java.util.UUID

@Profile("local-dev", "dev", "staging", "live")
@Component
internal class S3ResumeFileStore(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: S3ObjectStorageProperties,
) : ResumeFileStore {
    override fun store(memberId: UUID, upload: ResumeUpload): ResumeFile {
        val key = "resumes/$memberId/${UUID.randomUUID()}.pdf"
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(upload.contentType)
            .contentLength(upload.content.size.toLong())
            .build()

        try {
            s3Client.putObject(request, RequestBody.fromBytes(upload.content))
        } catch (exception: SdkException) {
            throw ResumeFileStorageException(exception)
        }

        return ResumeFile(
            key = key,
            originalName = upload.originalName,
            sizeBytes = upload.content.size.toLong(),
            contentType = upload.contentType,
        )
    }

    override fun read(file: ResumeFile): ByteArray {
        val request = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(file.key)
            .build()
        return try {
            s3Client.getObjectAsBytes(request).asByteArray()
        } catch (exception: SdkException) {
            throw ResumeFileStorageException(exception)
        }
    }

    override fun issueViewUrl(file: ResumeFile, ttl: Duration): String {
        val request = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(file.key)
            .build()
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(request)
            .build()
        return try {
            s3Presigner.presignGetObject(presignRequest).url().toExternalForm()
        } catch (exception: SdkException) {
            throw ResumeFileStorageException(exception)
        }
    }
}
