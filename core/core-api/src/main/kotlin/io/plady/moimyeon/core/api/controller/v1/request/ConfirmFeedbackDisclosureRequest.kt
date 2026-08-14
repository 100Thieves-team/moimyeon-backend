package io.plady.moimyeon.core.api.controller.v1.request

import java.util.UUID

data class ConfirmFeedbackDisclosureRequest(
    val roomId: UUID,
    val intervieweeMemberId: UUID,
)
