package io.plady.moimyeon.client.ai

import io.plady.moimyeon.core.domain.resume.DocumentSummarizationException
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.retry.TransientAiException
import java.io.ByteArrayOutputStream

class BedrockDocumentSummarizerTest {
    @Test
    fun `PDF 문서와 요약 지시를 전달하고 응답의 공백을 정리한다`() {
        val chatModel = RecordingChatModel("  백엔드 개발 경력 3년  ")
        val summarizer = BedrockDocumentSummarizer(ChatClient.builder(chatModel))

        val summary = summarizer.summarizePdf("pdf-content".toByteArray())

        assertThat(summary).isEqualTo("백엔드 개발 경력 3년")
        assertThat(chatModel.prompt.systemMessage.text).contains("추측하거나 만들지 마세요")
        assertThat(chatModel.prompt.userMessage.text).contains("한국어 2문장 이내")
        assertThat(chatModel.prompt.userMessage.media).hasSize(1)
    }

    @Test
    fun `Bedrock 문서 제한보다 큰 PDF는 텍스트를 추출해 전달한다`() {
        val chatModel = RecordingChatModel("백엔드 개발자")
        val summarizer = BedrockDocumentSummarizer(ChatClient.builder(chatModel))

        summarizer.summarizePdf(largePdfWith("Backend developer with Kotlin experience"))

        assertThat(chatModel.prompt.userMessage.text).contains("Backend developer with Kotlin experience")
        assertThat(chatModel.prompt.userMessage.media).isEmpty()
    }

    @Test
    fun `Bedrock 호출 실패를 도메인 포트 예외로 변환한다`() {
        val cause = TransientAiException("bedrock unavailable")
        val summarizer = BedrockDocumentSummarizer(ChatClient.builder(FailingChatModel(cause)))

        assertThatThrownBy { summarizer.summarizePdf("pdf-content".toByteArray()) }
            .isInstanceOf(DocumentSummarizationException::class.java)
            .hasCause(cause)
    }

    private fun largePdfWith(text: String): ByteArray {
        val pdf = ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                    content.newLineAtOffset(40f, 700f)
                    content.showText(text)
                    content.endText()
                }
                document.save(output)
            }
            output.toByteArray()
        }
        return pdf + ByteArray(BEDROCK_DOCUMENT_LIMIT_FOR_TEST - pdf.size + 1)
    }
}

private class FailingChatModel(
    private val exception: RuntimeException,
) : ChatModel {
    override fun call(prompt: Prompt): ChatResponse = throw exception
}

private class RecordingChatModel(
    private val response: String,
) : ChatModel {
    lateinit var prompt: Prompt

    override fun call(prompt: Prompt): ChatResponse {
        this.prompt = prompt
        return ChatResponse(listOf(Generation(AssistantMessage(response))))
    }
}

private const val BEDROCK_DOCUMENT_LIMIT_FOR_TEST = 4_500_000
