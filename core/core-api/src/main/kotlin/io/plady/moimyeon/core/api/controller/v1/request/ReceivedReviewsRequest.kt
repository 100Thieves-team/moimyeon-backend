package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

data class ReceivedReviewsRequest(
    val lastReviewId: Long? = null,
    val size: Int? = null,
) {
    fun toLastReviewId(): Long? {
        if (lastReviewId != null && lastReviewId <= 0) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return lastReviewId
    }

    fun toSize(): Int = size?.takeIf { it in SIZE_RANGE } ?: DEFAULT_SIZE

    companion object {
        private const val DEFAULT_SIZE = 20
        private val SIZE_RANGE = 1..50
    }
}
