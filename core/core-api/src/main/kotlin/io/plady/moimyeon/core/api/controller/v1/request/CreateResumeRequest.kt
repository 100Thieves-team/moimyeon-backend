package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile

data class CreateResumeRequest(
    val name: String,
    val file: MultipartFile,
) {
    fun validate() {
        val isPdf = file.contentType == MediaType.APPLICATION_PDF_VALUE &&
            file.originalFilename?.endsWith(PDF_EXTENSION, ignoreCase = true) == true
        if (name.isBlank() || file.isEmpty || file.size > MAX_FILE_SIZE_BYTES || !isPdf) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
    }
}

private const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
private const val PDF_EXTENSION = ".pdf"
