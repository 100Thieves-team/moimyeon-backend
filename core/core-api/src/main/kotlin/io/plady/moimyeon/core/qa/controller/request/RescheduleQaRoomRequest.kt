package io.plady.moimyeon.core.qa.controller.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.time.LocalDateTime

data class RescheduleQaRoomRequest(
    val startAt: LocalDateTime? = null,
) {
    fun toStartAt(): LocalDateTime = startAt ?: throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
}
