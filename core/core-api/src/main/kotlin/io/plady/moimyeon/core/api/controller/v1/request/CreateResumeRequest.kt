package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.resume.ResumeUpload
import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile

data class CreateResumeRequest(
    val file: MultipartFile,
) {
    fun toUpload(): ResumeUpload {
        val originalName = file.originalFilename.orEmpty()
        val isPdf = file.contentType == MediaType.APPLICATION_PDF_VALUE &&
            originalName.endsWith(PDF_EXTENSION, ignoreCase = true)
        if (
            originalName.isBlank() ||
            originalName.length > MAX_NAME_LENGTH ||
            file.isEmpty ||
            file.size > MAX_FILE_SIZE_BYTES ||
            !isPdf
        ) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return ResumeUpload(
            originalName = originalName,
            contentType = file.contentType.orEmpty(),
            content = file.bytes,
        )
    }
}

private const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
private const val MAX_NAME_LENGTH = 100
private const val PDF_EXTENSION = ".pdf"
