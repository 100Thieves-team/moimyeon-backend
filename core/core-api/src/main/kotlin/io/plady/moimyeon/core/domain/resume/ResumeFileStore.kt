package io.plady.moimyeon.core.domain.resume

import java.util.UUID

interface ResumeFileStore {
    fun store(memberId: UUID, upload: ResumeUpload): ResumeFile

    fun read(file: ResumeFile): ByteArray
}
