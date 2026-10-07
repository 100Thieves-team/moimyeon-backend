package io.plady.moimyeon.core.qa.controller.request

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

data class ChangeQaMemberStatusRequest(
    val status: MemberStatus? = null,
) {
    fun toStatus(): MemberStatus = status ?: throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
}
