package io.plady.moimyeon.core.domain.resume

import java.util.UUID

interface ResumeFileStore {
    fun store(memberId: UUID, upload: ResumeUpload, deadline: ResumeSummaryDeadline): ResumeFile

    fun read(file: ResumeFile, deadline: ResumeSummaryDeadline): ByteArray
}
