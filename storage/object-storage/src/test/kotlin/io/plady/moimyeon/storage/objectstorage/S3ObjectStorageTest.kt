package io.plady.moimyeon.storage.objectstorage

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.domain.storage.ObjectStorageException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.time.Duration

class S3ObjectStorageTest {
    private val s3Client = mockk<S3Client>()
    private val objectStorage = S3ObjectStorage(
        s3Client,
        properties(),
    )

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
    fun `지정한 키와 콘텐츠 정보로 비공개 버킷에 객체를 저장한다`() {
        val request = slot<PutObjectRequest>()
        every {
            s3Client.putObject(capture(request), any<RequestBody>())
        } returns PutObjectResponse.builder().build()

        objectStorage.store("resumes/member/resume.pdf", "application/pdf", "pdf-content".toByteArray())

        assertThat(request.captured.bucket()).isEqualTo("resume-bucket")
        assertThat(request.captured.key()).isEqualTo("resumes/member/resume.pdf")
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

        val storedContent = objectStorage.read("resumes/member/resume.pdf")

        assertThat(storedContent).isEqualTo(content)
        assertThat(request.captured.bucket()).isEqualTo("resume-bucket")
        assertThat(request.captured.key()).isEqualTo("resumes/member/resume.pdf")
    }

    @Test
    fun `S3 읽기 실패를 도메인 포트 예외로 변환한다`() {
        val cause = SdkException.create("s3 unavailable", null)
        every { s3Client.getObjectAsBytes(any<GetObjectRequest>()) } throws cause

        assertThatThrownBy { objectStorage.read("resumes/member/resume.pdf") }
            .isInstanceOf(ObjectStorageException::class.java)
            .hasCause(cause)
    }

    private fun properties() = S3ObjectStorageProperties(
        bucket = "resume-bucket",
        region = "ap-northeast-2",
        apiCallTimeout = Duration.ofSeconds(30),
        apiCallAttemptTimeout = Duration.ofSeconds(10),
    )
}
