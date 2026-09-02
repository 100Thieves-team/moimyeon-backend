package io.plady.moimyeon.storage.objectstorage

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeFileStorageException
import io.plady.moimyeon.core.domain.resume.ResumeUpload
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Duration
import java.util.UUID

class S3ResumeFileStoreTest {
    private val s3Client = mockk<S3Client>()
    private val fileStore = S3ResumeFileStore(
        s3Client,
        presigner(),
        properties(),
    )

    @Test
    fun `열람 URL 은 객체 키와 5분 만료를 서명에 담는다`() {
        val file = ResumeFile("resumes/member/resume.pdf", "resume.pdf", 11, "application/pdf")

        val url = fileStore.issueViewUrl(file, Duration.ofMinutes(5))

        assertThat(url)
            .contains("resume-bucket")
            .contains("resumes/member/resume.pdf")
            .contains("X-Amz-Expires=300")
    }

    @Test
    fun `S3 호출 전체와 개별 시도에 제한 시간을 적용한다`() {
        val client = S3ObjectStorageConfig().s3Client(properties())

        client.use {
            val override = it.serviceClientConfiguration().overrideConfiguration()
            assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(30))
            assertThat(override.apiCallAttemptTimeout()).contains(Duration.ofSeconds(10))
        }
    }

    @Test
    fun `이력서 원본을 회원별 경로에 저장하고 파일 정보를 만든다`() {
        val request = slot<PutObjectRequest>()
        val memberId = UUID.randomUUID()
        val upload = ResumeUpload("backend.pdf", "application/pdf", "pdf-content".toByteArray())
        every {
            s3Client.putObject(capture(request), any<RequestBody>())
        } returns PutObjectResponse.builder().build()

        val storedFile = fileStore.store(memberId, upload)

        assertThat(request.captured.bucket()).isEqualTo("resume-bucket")
        assertThat(request.captured.key()).isEqualTo(storedFile.key)
        assertThat(storedFile.key).startsWith("resumes/$memberId/").endsWith(".pdf")
        assertThat(storedFile.originalName).isEqualTo(upload.originalName)
        assertThat(storedFile.sizeBytes).isEqualTo(upload.content.size.toLong())
        assertThat(storedFile.contentType).isEqualTo(upload.contentType)
        assertThat(request.captured.contentType()).isEqualTo("application/pdf")
        assertThat(request.captured.contentLength()).isEqualTo(11)
        verify(exactly = 1) { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) }
    }

    @Test
    fun `버킷의 객체를 키로 읽는다`() {
        val request = slot<GetObjectRequest>()
        val content = "pdf-content".toByteArray()
        every {
            s3Client.getObjectAsBytes(capture(request))
        } returns ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content)

        val file = ResumeFile("resumes/member/resume.pdf", "resume.pdf", 11, "application/pdf")
        val storedContent = fileStore.read(file)

        assertThat(storedContent).isEqualTo(content)
        assertThat(request.captured.bucket()).isEqualTo("resume-bucket")
        assertThat(request.captured.key()).isEqualTo("resumes/member/resume.pdf")
    }

    @Test
    fun `S3 읽기 실패를 이력서 파일 실패로 변환한다`() {
        val cause = SdkException.create("s3 unavailable", null)
        every { s3Client.getObjectAsBytes(any<GetObjectRequest>()) } throws cause
        val file = ResumeFile("resumes/member/resume.pdf", "resume.pdf", 11, "application/pdf")

        assertThatThrownBy { fileStore.read(file) }
            .isInstanceOf(ResumeFileStorageException::class.java)
            .hasCause(cause)
    }

    private fun properties() = S3ObjectStorageProperties(
        bucket = "resume-bucket",
        region = "ap-northeast-2",
        apiCallTimeout = Duration.ofSeconds(30),
        apiCallAttemptTimeout = Duration.ofSeconds(10),
    )

    // 서명은 로컬 계산이라 네트워크 없이 실제 presigner 로 검증한다. 자격 증명만 고정 값으로 채운다.
    private fun presigner(): S3Presigner = S3Presigner.builder()
        .region(Region.of("ap-northeast-2"))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access-key", "test-secret-key")),
        )
        .build()
}
