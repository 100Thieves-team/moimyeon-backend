package io.plady.moimyeon.core.domain.resume

import java.util.UUID

interface ResumeFileStorage {
    fun store(memberId: UUID, upload: ResumeUpload): ResumeFile
}
