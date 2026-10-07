package io.plady.moimyeon.client.bedrock

import io.plady.moimyeon.core.domain.resume.ResumeSummaryDeadline
import io.plady.moimyeon.core.domain.resume.ResumeSummaryGenerationException
import io.plady.moimyeon.core.domain.resume.ResumeSummaryTimeSource
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.text.Normalizer

class ResumePdfTextExtractorTest {
    private val extractor = ResumePdfTextExtractor(ResumeSummaryTimeSource { 0L })

    @Test
    fun `텍스트 PDF를 화면 위치 순서로 추출한다`() {
        val content = pdfWithTwoColumns()

        val text = extract(content)

        assertThat(text).containsSubsequence(
            "Left column Right column",
            "Company: Moimyeon Skills: Kotlin and Spring",
            "Role: Backend developer Result: Reduced processing time",
        )
    }

    @Test
    fun `분리된 한글 자모는 NFC 완성형으로 정규화한다`() {
        val decomposed = Normalizer.normalize("김개발 백엔드 개발자", Normalizer.Form.NFD)

        val normalized = normalizeExtractedResumeText(decomposed)

        assertThat(normalized).isEqualTo("김개발 백엔드 개발자")
    }

    @Test
    fun `명백하게 깨진 Unicode 문자가 추출되면 거부한다`() {
        listOf("경력 3년\uFFFD", "경력\u0000 3년").forEach { text ->
            assertThatThrownBy { normalizeExtractedResumeText(text) }
                .isInstanceOf(ResumeSummaryGenerationException::class.java)
        }
    }

    @Test
    fun `아이콘 폰트의 Private Use 문자는 제거하고 나머지 텍스트를 사용한다`() {
        assertThat(normalizeExtractedResumeText("\uE000 Kotlin 백엔드 개발자"))
            .isEqualTo("Kotlin 백엔드 개발자")
    }

    @Test
    fun `텍스트가 없거나 이미지로만 구성된 PDF는 거부한다`() {
        assertThatThrownBy { extract(imageOnlyPdf()) }
            .isInstanceOf(ResumeSummaryGenerationException::class.java)
    }

    @Test
    fun `암호화된 PDF는 거부한다`() {
        assertThatThrownBy { extract(encryptedPdf()) }
            .isInstanceOf(ResumeSummaryGenerationException::class.java)
    }

    @Test
    fun `손상된 PDF는 거부한다`() {
        assertThatThrownBy { extract("%PDF-broken".toByteArray()) }
            .isInstanceOf(ResumeSummaryGenerationException::class.java)
    }

    @Test
    fun `최대 페이지 수를 초과한 PDF는 거부한다`() {
        assertThatThrownBy { extract(pdfWithPageCount(51)) }
            .isInstanceOf(ResumeSummaryGenerationException::class.java)
    }

    @Test
    fun `추출 문자 수 상한을 초과한 PDF는 거부한다`() {
        assertThatThrownBy { extract(textPdf("A".repeat(60_001))) }
            .isInstanceOf(ResumeSummaryGenerationException::class.java)
    }

    @Test
    fun `최대 50페이지와 60000자 경계는 허용한다`() {
        assertThat(extract(pdfWithTextPageCount(50))).contains("Page 50")
        assertThat(extract(textPdf("A".repeat(60_000)))).hasSize(60_000)
    }

    @Test
    fun `텍스트 추출 중 전체 처리 기한이 만료되면 거부한다`() {
        val timeSource = ExtractorSequenceTimeSource(listOf(0L, java.time.Duration.ofSeconds(46).toNanos()))
        val deadlineExtractor = ResumePdfTextExtractor(timeSource)

        assertThatThrownBy {
            deadlineExtractor.extract(textPdf("Backend developer"), ResumeSummaryDeadline.start(0L))
        }.isInstanceOf(ResumeSummaryGenerationException::class.java)
    }

    private fun pdfWithTwoColumns(): ByteArray {
        return createPdf { document, page ->
            PDPageContentStream(document, page).use { content ->
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.beginText()
                content.newLineAtOffset(40f, 700f)
                content.showText("Left column")
                content.newLineAtOffset(0f, -20f)
                content.showText("Company: Moimyeon")
                content.newLineAtOffset(0f, -20f)
                content.showText("Role: Backend developer")
                content.endText()
                content.beginText()
                content.newLineAtOffset(300f, 700f)
                content.showText("Right column")
                content.newLineAtOffset(0f, -20f)
                content.showText("Skills: Kotlin and Spring")
                content.newLineAtOffset(0f, -20f)
                content.showText("Result: Reduced processing time")
                content.endText()
            }
        }
    }

    private fun imageOnlyPdf(): ByteArray {
        return createPdf { document, page ->
            val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
            val pdfImage = LosslessFactory.createFromImage(document, image)
            PDPageContentStream(document, page).use { content ->
                content.drawImage(pdfImage, 0f, 0f)
            }
        }
    }

    private fun encryptedPdf(): ByteArray {
        return createPdf { document, page ->
            PDPageContentStream(document, page).use { content ->
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.beginText()
                content.newLineAtOffset(40f, 700f)
                content.showText("Backend developer")
                content.endText()
            }
            document.protect(
                StandardProtectionPolicy(
                    "owner-password",
                    "",
                    AccessPermission(),
                ),
            )
        }
    }

    private fun pdfWithPageCount(pageCount: Int): ByteArray {
        return ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                repeat(pageCount) {
                    document.addPage(PDPage())
                }
                document.save(output)
            }
            output.toByteArray()
        }
    }

    private fun pdfWithTextPageCount(pageCount: Int): ByteArray {
        return ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                repeat(pageCount) { index ->
                    val page = PDPage()
                    document.addPage(page)
                    PDPageContentStream(document, page).use { content ->
                        content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                        content.beginText()
                        content.newLineAtOffset(40f, 700f)
                        content.showText("Page ${index + 1}")
                        content.endText()
                    }
                }
                document.save(output)
            }
            output.toByteArray()
        }
    }

    private fun textPdf(text: String): ByteArray {
        return createPdf { document, page ->
            PDPageContentStream(document, page).use { content ->
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.beginText()
                content.newLineAtOffset(40f, 700f)
                content.showText(text)
                content.endText()
            }
        }
    }

    private fun createPdf(configure: (PDDocument, PDPage) -> Unit): ByteArray {
        return ByteArrayOutputStream().use { output ->
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                configure(document, page)
                document.save(output)
            }
            output.toByteArray()
        }
    }

    private fun extract(content: ByteArray): String {
        return extractor.extract(content, ResumeSummaryDeadline.start(0L))
    }
}

private class ExtractorSequenceTimeSource(
    private val values: List<Long>,
) : ResumeSummaryTimeSource {
    private var index = 0

    override fun nanoTime(): Long = values.getOrElse(index++) { values.last() }
}
