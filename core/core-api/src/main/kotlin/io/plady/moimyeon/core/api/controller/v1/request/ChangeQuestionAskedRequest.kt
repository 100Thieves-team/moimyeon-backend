package io.plady.moimyeon.core.api.controller.v1.request

import java.util.UUID

data class ChangeQuestionAskedRequest(
    val roomId: UUID,
    val intervieweeMemberId: UUID,
    val asked: Boolean,
)
