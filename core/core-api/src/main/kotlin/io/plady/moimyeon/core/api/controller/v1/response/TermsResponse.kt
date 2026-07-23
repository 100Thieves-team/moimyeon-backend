package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.enums.TermsType
import java.time.LocalDateTime
import java.util.UUID

data class TermsListResponse(
    val terms: List<TermsResponse>,
)

data class TermsResponse(
    val termsId: UUID,
    val type: TermsType,
    val version: String,
    val title: String,
    val content: String,
    val required: Boolean,
    val effectiveFrom: LocalDateTime,
)
