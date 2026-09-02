package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.room.ResumeOriginalViewUrl
import java.time.LocalDateTime

data class ResumeOriginalViewUrlResponse(
    val url: String,
    val expiresAt: LocalDateTime,
) {
    companion object {
        fun from(viewUrl: ResumeOriginalViewUrl): ResumeOriginalViewUrlResponse {
            return ResumeOriginalViewUrlResponse(
                url = viewUrl.url,
                expiresAt = viewUrl.expiresAt,
            )
        }
    }
}
