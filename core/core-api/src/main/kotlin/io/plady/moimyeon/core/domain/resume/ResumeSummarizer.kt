package io.plady.moimyeon.core.domain.resume

import java.util.UUID

interface ResumeSummarizer {
    fun summarize(resumeId: UUID)
}
