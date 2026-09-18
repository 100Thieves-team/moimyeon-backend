package io.plady.moimyeon.core.domain.resume

import java.time.Duration
import java.util.UUID

interface ResumeFileStore {
    fun store(memberId: UUID, upload: ResumeUpload, deadline: ResumeSummaryDeadline): ResumeFile

    fun read(file: ResumeFile, deadline: ResumeSummaryDeadline): ByteArray

    // 발급 후에는 권한 회수와 무관하게 서명 만료까지 살아 있다(MOI-414 D3-8).
    fun issueViewUrl(file: ResumeFile, ttl: Duration): ResumeFileViewUrl
}
