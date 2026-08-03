package io.plady.moimyeon.core.domain.resume

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.storage.ObjectStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ResumeFileStorageTest {
    private val objectStorage = mockk<ObjectStorage>()
    private val resumeFileStorage = ResumeFileStorage(objectStorage)

    @Test
    fun `이력서 원본을 회원별 S3 경로에 저장하고 파일 정보를 만든다`() {
        val memberId = UUID.randomUUID()
        val upload = ResumeUpload("backend.pdf", "application/pdf", "pdf-content".toByteArray())
        every { objectStorage.store(any(), upload.contentType, upload.content) } returns Unit

        val storedFile = resumeFileStorage.store(memberId, upload)

        assertThat(storedFile.key).startsWith("resumes/$memberId/").endsWith(".pdf")
        assertThat(storedFile.originalName).isEqualTo(upload.originalName)
        assertThat(storedFile.sizeBytes).isEqualTo(upload.content.size.toLong())
        assertThat(storedFile.contentType).isEqualTo(upload.contentType)
        verify(exactly = 1) { objectStorage.store(storedFile.key, upload.contentType, upload.content) }
    }

    @Test
    fun `보관한 이력서 원본을 파일 키로 읽는다`() {
        val file = ResumeFile("resumes/member/resume.pdf", "resume.pdf", 11, "application/pdf")
        val content = "pdf-content".toByteArray()
        every { objectStorage.read(file.key) } returns content

        val storedContent = resumeFileStorage.read(file)

        assertThat(storedContent).isSameAs(content)
        verify(exactly = 1) { objectStorage.read(file.key) }
    }
}
