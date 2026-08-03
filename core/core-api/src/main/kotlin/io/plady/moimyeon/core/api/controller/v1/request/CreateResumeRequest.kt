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
        if (
            originalName.isBlank() ||
            originalName.length > MAX_NAME_LENGTH ||
            file.isEmpty ||
            file.size > MAX_FILE_SIZE_BYTES ||
            file.contentType != MediaType.APPLICATION_PDF_VALUE ||
            !originalName.endsWith(PDF_EXTENSION, ignoreCase = true)
        ) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }

        val content = file.bytes
        if (!content.hasPdfSignature()) {
            throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)
        }
        return ResumeUpload(
            originalName = originalName,
            contentType = file.contentType.orEmpty(),
            content = content,
        )
    }
}

private fun ByteArray.hasPdfSignature(): Boolean {
    return size >= PDF_SIGNATURE.size && PDF_SIGNATURE.indices.all { this[it] == PDF_SIGNATURE[it] }
}

private const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
private const val MAX_NAME_LENGTH = 100
private const val PDF_EXTENSION = ".pdf"
private val PDF_SIGNATURE = "%PDF-".toByteArray()
