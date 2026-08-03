package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.domain.storage.ObjectStorage
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

@Profile("local-dev", "dev", "staging", "live")
@Component
class ResumeFileStorage(
    private val objectStorage: ObjectStorage,
) {
    fun store(memberId: UUID, upload: ResumeUpload): ResumeFile {
        val key = "resumes/$memberId/${UUID.randomUUID()}.pdf"
        objectStorage.store(key, upload.contentType, upload.content)
        return ResumeFile(
            key = key,
            originalName = upload.originalName,
            sizeBytes = upload.content.size.toLong(),
            contentType = upload.contentType,
        )
    }

    fun read(file: ResumeFile): ByteArray = objectStorage.read(file.key)
}
