package io.plady.moimyeon.core.domain.terms

import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.enums.TermsType
import java.time.LocalDateTime
import java.util.UUID

data class Terms(
    val id: UUID,
    val type: TermsType,
    val version: String,
    val title: String,
    val content: String,
    val required: Boolean,
    val effectiveFrom: LocalDateTime,
    val status: TermsStatus,
)
