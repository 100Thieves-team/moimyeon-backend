package io.plady.moimyeon.core.qa.controller.request

import io.plady.moimyeon.core.qa.QaDataCondition
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import java.util.UUID

data class QaDataRequest(
    val prefix: String = QaDataCondition.QA_MARKER,
    val hostMemberId: String? = null,
    val includeMembers: Boolean = false,
) {
    fun toCondition(): QaDataCondition {
        if (!QaDataCondition.hasQaMarker(prefix)) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        val hostMemberId = hostMemberId?.let {
            runCatching { UUID.fromString(it) }.getOrElse { throw CoreApiException(CoreApiErrorType.INVALID_REQUEST) }
        }
        return QaDataCondition(prefix = prefix, hostMemberId = hostMemberId, includeMembers = includeMembers)
    }
}
