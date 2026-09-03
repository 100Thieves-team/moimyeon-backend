package io.plady.moimyeon.client.bedrock

import io.plady.moimyeon.core.domain.resume.ResumeSummaryDeadline
import io.plady.moimyeon.core.domain.resume.ResumeSummaryGenerationException
import io.plady.moimyeon.core.domain.resume.ResumeSummaryTimeSource
import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.springframework.stereotype.Component
import java.io.IOException
import java.text.Normalizer
import java.time.Duration

@Component
internal class ResumePdfTextExtractor(
    private val timeSource: ResumeSummaryTimeSource,
) {
    fun extract(content: ByteArray, deadline: ResumeSummaryDeadline): String {
        val checkDeadline = {
            if (!deadline.hasTimeFor(Duration.ZERO, timeSource.nanoTime())) {
                throw ResumeSummaryGenerationException()
            }
        }
        checkDeadline()
        val text = try {
            Loader.loadPDF(content).use { document ->
                if (document.isEncrypted) {
                    throw ResumeSummaryGenerationException()
                }
                if (document.numberOfPages > MAX_RESUME_PDF_PAGES) {
                    throw ResumeSummaryGenerationException()
                }
                LimitedPdfTextStripper(MAX_RESUME_EXTRACTED_TEXT_LENGTH, checkDeadline).apply {
                    sortByPosition = true
                }.getText(document)
            }
        } catch (exception: ResumeSummaryGenerationException) {
            throw exception
        } catch (exception: PdfTextExtractionLimitExceededException) {
            throw ResumeSummaryGenerationException(exception)
        } catch (exception: IOException) {
            throw ResumeSummaryGenerationException(exception)
        }
        return normalizeExtractedResumeText(text)
    }
}

internal fun normalizeExtractedResumeText(text: String): String {
    val withoutPrivateUseCharacters = StringBuilder().apply {
        text.codePoints().forEach { codePoint ->
            if (Character.getType(codePoint) == Character.PRIVATE_USE.toInt()) {
                append(' ')
            } else {
                appendCodePoint(codePoint)
            }
        }
    }.toString()
    if (withoutPrivateUseCharacters.codePoints().anyMatch(::isInvalidExtractedCodePoint)) {
        throw ResumeSummaryGenerationException()
    }
    val normalized = Normalizer.normalize(withoutPrivateUseCharacters, Normalizer.Form.NFC)
        .lineSequence()
        .map { it.trim().replace(WHITESPACE_WITHIN_LINE, " ") }
        .filter { it.isNotBlank() }
        .joinToString("\n")
    if (normalized.isBlank() || normalized.length > MAX_RESUME_EXTRACTED_TEXT_LENGTH) {
        throw ResumeSummaryGenerationException()
    }
    return normalized
}

private class LimitedPdfTextStripper(
    private val maxCharacters: Int,
    private val checkDeadline: () -> Unit,
) : PDFTextStripper() {
    private var extractedCharacters = 0

    override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        checkDeadline()
        super.processOperator(operator, operands)
    }

    override fun processTextPosition(text: TextPosition) {
        checkDeadline()
        val characterCount = text.unicode.length
        if (characterCount > maxCharacters - extractedCharacters) {
            throw PdfTextExtractionLimitExceededException()
        }
        extractedCharacters += characterCount
        super.processTextPosition(text)
    }
}

private class PdfTextExtractionLimitExceededException : RuntimeException()

private fun isInvalidExtractedCodePoint(codePoint: Int): Boolean {
    val isDisallowedControlCharacter = Character.isISOControl(codePoint) &&
        codePoint !in ALLOWED_CONTROL_CHARACTERS
    return codePoint == REPLACEMENT_CHARACTER || isDisallowedControlCharacter
}

private const val REPLACEMENT_CHARACTER = 0xFFFD
internal const val MAX_RESUME_PDF_PAGES = 50
internal const val MAX_RESUME_EXTRACTED_TEXT_LENGTH = 60_000
private val ALLOWED_CONTROL_CHARACTERS = setOf('\n'.code, '\r'.code, '\t'.code)
private val WHITESPACE_WITHIN_LINE = Regex("[\\t ]+")
