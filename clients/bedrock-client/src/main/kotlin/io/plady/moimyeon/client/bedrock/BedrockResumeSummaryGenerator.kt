package io.plady.moimyeon.client.bedrock

import io.plady.moimyeon.core.domain.resume.ResumeSummaryGenerationException
import io.plady.moimyeon.core.domain.resume.ResumeSummaryGenerator
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.content.Media
import org.springframework.ai.retry.NonTransientAiException
import org.springframework.ai.retry.TransientAiException
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import java.io.IOException

@Profile("local-dev", "dev", "staging", "live")
@Component
internal class BedrockResumeSummaryGenerator(
    chatClientBuilder: ChatClient.Builder,
) : ResumeSummaryGenerator {
    private val chatClient = chatClientBuilder.build()

    override fun generate(content: ByteArray): String {
        val response = try {
            chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user { user ->
                    if (content.size <= BEDROCK_DOCUMENT_MAX_BYTES) {
                        user.text(USER_PROMPT)
                            .media(Media.Format.DOC_PDF, NeutralPdfResource(content))
                    } else {
                        user.text(USER_PROMPT + extractText(content))
                    }
                }
                .call()
                .content()
                ?.trim()
        } catch (exception: TransientAiException) {
            throw ResumeSummaryGenerationException(exception)
        } catch (exception: NonTransientAiException) {
            throw ResumeSummaryGenerationException(exception)
        } catch (exception: SdkException) {
            throw ResumeSummaryGenerationException(exception)
        }

        if (response.isNullOrBlank()) {
            throw ResumeSummaryGenerationException()
        }
        return response
    }

    private fun extractText(content: ByteArray): String {
        val text = try {
            Loader.loadPDF(content).use { document ->
                PDFTextStripper().getText(document)
            }
                .lineSequence()
                .map { it.trim().replace(WHITESPACE_WITHIN_LINE, " ") }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        } catch (exception: IOException) {
            throw ResumeSummaryGenerationException(exception)
        }

        if (text.isBlank()) {
            throw ResumeSummaryGenerationException()
        }
        return "\n\n<resume>\n${text.take(MAX_EXTRACTED_TEXT_LENGTH)}\n</resume>"
    }
}

private class NeutralPdfResource(content: ByteArray) : ByteArrayResource(content) {
    override fun getFilename(): String = "resume.pdf"
}

private const val SYSTEM_PROMPT =
    "당신은 채용 이력서를 정확하게 요약하는 도우미입니다. 문서에 없는 사실을 추측하거나 만들지 마세요."

private const val USER_PROMPT =
    "이 이력서를 다른 사용자가 빠르게 파악할 수 있도록 한국어 2문장 이내로 요약하세요. " +
        "주요 직무, 경력 수준, 핵심 기술과 대표 경험만 포함하고 개인정보는 반복하지 마세요."

private const val BEDROCK_DOCUMENT_MAX_BYTES = 4_500_000
private const val MAX_EXTRACTED_TEXT_LENGTH = 60_000
private val WHITESPACE_WITHIN_LINE = Regex("[\\t ]+")
