package io.plady.moimyeon.storage.objectstorage

import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeFileStorageException
import io.plady.moimyeon.core.domain.resume.ResumeFileStore
import io.plady.moimyeon.core.domain.resume.ResumeFileViewUrl
import io.plady.moimyeon.core.domain.resume.ResumeSummaryDeadline
import io.plady.moimyeon.core.domain.resume.ResumeSummaryTimeSource
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
import java.time.temporal.ChronoUnit
import java.util.UUID

@Profile("local-dev", "dev", "staging", "live")
@Component
internal class S3ResumeFileStore(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: S3ObjectStorageProperties,
    private val timeSource: ResumeSummaryTimeSource,
) : ResumeFileStore {
    override fun store(memberId: UUID, upload: ResumeUpload, deadline: ResumeSummaryDeadline): ResumeFile {
        val key = "resumes/$memberId/${UUID.randomUUID()}.pdf"
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(upload.contentType)
            .contentLength(upload.content.size.toLong())
            .overrideConfiguration { it.apiCallTimeout(requestTimeout(deadline)) }
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

    override fun read(file: ResumeFile, deadline: ResumeSummaryDeadline): ByteArray {
        val request = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(file.key)
            .overrideConfiguration { it.apiCallTimeout(requestTimeout(deadline)) }
            .build()
        return try {
            s3Client.getObjectAsBytes(request).asByteArray()
        } catch (exception: SdkException) {
            throw ResumeFileStorageException(exception)
        }
    }

    override fun issueViewUrl(file: ResumeFile, ttl: Duration): ResumeFileViewUrl {
        val request = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(file.key)
            .build()
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(request)
            .build()
        return try {
            val signed = s3Presigner.presignGetObject(presignRequest)
            ResumeFileViewUrl(
                url = signed.url().toExternalForm(),
                // SigV4의 서명 시각과 유효 기간은 초 단위다.
                expiresAt = signed.expiration().truncatedTo(ChronoUnit.SECONDS),
            )
        } catch (exception: SdkException) {
            throw ResumeFileStorageException(exception)
        }
    }

    private fun requestTimeout(deadline: ResumeSummaryDeadline): java.time.Duration {
        val remaining = deadline.remainingDuration(timeSource.nanoTime())
        if (remaining.isZero || remaining.isNegative) {
            throw ResumeFileStorageException(IllegalStateException("Resume summary deadline exceeded"))
        }
        return minOf(remaining, properties.apiCallTimeout)
    }
}
