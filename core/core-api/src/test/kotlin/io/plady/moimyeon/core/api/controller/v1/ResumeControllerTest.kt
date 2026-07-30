package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.partWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.requestParts
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ResumeControllerTest : RestDocsTest() {
    private val uploadSummary = "이력서 업로드"
    private val uploadDescription =
        "소개와 이력서(B·08) 단계의 이력서 PDF 업로드. multipart/form-data 로 파일 파트(file)를 받는다. " +
            "업로드 직후 AI 요약은 PROCESSING 이며, 이후 이력서 조회로 폴링해 DONE 을 확인한다(§4.5). " +
            "(모킹: 파일을 저장하지 않고 메타데이터만 에코하며 고정 resumeId(90001)를 반환한다)"
    private val detailSummary = "이력서 조회"
    private val detailDescription =
        "업로드한 이력서의 상태와 AI 요약을 조회한다. 업로드 후 요약 완료(DONE)를 폴링하는 용도. " +
            "(모킹: 조회 시 항상 요약 완료(DONE)를 반환한다)"

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(ResumeController())
    }

    @Test
    fun uploadResume() {
        val file = MockMultipartFile(
            "file",
            "김민수_이력서.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "%PDF-1.4 mock resume bytes".toByteArray(),
        )

        mockMvc.perform(multipart("/v1/resumes").file(file))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "uploadResume",
                    uploadSummary,
                    uploadDescription,
                    requestParts(
                        partWithName("file").description("이력서 PDF 파일 (multipart/form-data 파트명: file)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.resumeId").type(JsonFieldType.NUMBER).description("이력서 id (면접 생성 시 참조)"),
                        fieldWithPath("data.fileName").type(JsonFieldType.STRING).description("원본 파일명"),
                        fieldWithPath("data.fileSize").type(JsonFieldType.NUMBER).description("파일 크기(바이트)"),
                        fieldWithPath("data.fileSizeLabel").type(JsonFieldType.STRING).description("파일 크기 표시명 (240KB)"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("이력서 상태 (UPLOADED)"),
                        fieldWithPath("data.aiSummary.status").type(JsonFieldType.STRING).description("AI 요약 상태 (PROCESSING | DONE | FAILED)"),
                        fieldWithPath("data.aiSummary.text").type(JsonFieldType.STRING).optional().description("AI 요약 본문 (PROCESSING 중에는 null)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun resumeDetail() {
        mockMvc.perform(get("/v1/resumes/{resumeId}", 90001L))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "resumeDetail",
                    detailSummary,
                    detailDescription,
                    pathParameters(
                        parameterWithName("resumeId").description("조회할 이력서 id"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.resumeId").type(JsonFieldType.NUMBER).description("이력서 id"),
                        fieldWithPath("data.fileName").type(JsonFieldType.STRING).description("원본 파일명"),
                        fieldWithPath("data.fileSize").type(JsonFieldType.NUMBER).description("파일 크기(바이트)"),
                        fieldWithPath("data.fileSizeLabel").type(JsonFieldType.STRING).description("파일 크기 표시명 (240KB)"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("이력서 상태 (UPLOADED)"),
                        fieldWithPath("data.aiSummary.status").type(JsonFieldType.STRING).description("AI 요약 상태 (PROCESSING | DONE | FAILED)"),
                        fieldWithPath("data.aiSummary.text").type(JsonFieldType.STRING).optional().description("AI 요약 본문 (DONE 이면 채워짐)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
