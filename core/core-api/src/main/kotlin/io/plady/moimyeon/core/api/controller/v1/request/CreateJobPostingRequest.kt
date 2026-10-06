package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.jobposting.JobPostingCreationCommand
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException

data class CreateJobPostingRequest(
    val companyId: Long,
    val url: String,
    val postingName: String,
) {
    fun toCommand(): JobPostingCreationCommand {
        val trimmedUrl = url.trim()
        val trimmedName = postingName.trim()
        if (companyId <= 0) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        if (trimmedUrl.isBlank() || trimmedUrl.length > URL_MAX_LENGTH || !trimmedUrl.isHttpUrl()) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        if (trimmedName.isBlank() || trimmedName.length > POSTING_NAME_MAX_LENGTH) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return JobPostingCreationCommand(companyId = companyId, url = trimmedUrl, postingName = trimmedName)
    }

    companion object {
        private const val URL_MAX_LENGTH = 2000
        private const val POSTING_NAME_MAX_LENGTH = 100
    }
}
