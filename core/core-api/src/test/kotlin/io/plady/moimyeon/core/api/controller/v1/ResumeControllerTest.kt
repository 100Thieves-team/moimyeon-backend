package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.partWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.requestParts
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class ResumeControllerTest : RestDocsTest() {
    private val memberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val principal = Principal { memberId.toString() }
    private val defaultResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000101")
    private val processingResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000103")
    private val deletedResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000104")
    private val failedResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000105")
    private val unknownResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000404")

    private val resumesSummary = "보관 이력서 목록 조회"
    private val resumesDescription =
        "인증 회원의 보관 이력서를 마이페이지·룸 생성·참가 신청에서 함께 사용할 수 있는 동일한 자원 형태로 반환한다. " +
            "삭제한 이력서는 목록에서 제외되며, 기본 이력서를 먼저 보여준다. 최대 보관 개수는 최신 PRD 기준 10개다. " +
            "신청 화면에서는 선택지를 열 때 조회하며 개별 AI 요약 상태를 확인하는 폴링에는 단건 조회 API를 사용한다. " +
            "AI 요약은 DONE·PROCESSING·FAILED 상태를 가지며 준비 중이면 text가 null이다. PROCESSING은 시작 후 최대 1분이며, " +
            "시간이 지나면 AI를 자동 재호출하지 않고 FAILED로 확정한다. 인증 정보가 없으면 401(E1102)로 응답한다. " +
            "목 API는 고정 이력서 4건(DONE 2건, PROCESSING 1건, FAILED 1건)을 반환한다."
    private val resumeSummary = "보관 이력서 단건 조회"
    private val resumeDescription =
        "인증 회원이 소유한 이력서 한 건을 조회한다. AI 요약이 PROCESSING이면 클라이언트는 이 API를 일정 간격으로 폴링하고 " +
            "DONE 또는 FAILED가 되면 중단한다. PROCESSING은 시작 후 최대 1분이며 시간 초과 시 자동 재처리 없이 FAILED가 된다. " +
            "SSE는 제공하지 않는다. 식별자 형식 오류는 400(E400), " +
            "존재하지 않거나 본인 소유가 아닌 이력서는 404(E1010), 인증 정보 없음은 401(E1102)로 응답한다."
    private val createResumeSummary = "이력서 등록"
    private val createResumeDescription =
        "PDF 파일을 이력서 보관함에 등록하며 이력서 이름은 업로드한 원본 파일명을 사용한다. PDF만 허용하고 파일 크기는 10MB 이하여야 한다. " +
            "등록한 이력서는 수정하지 않으며 내용 변경은 새 등록으로 처리한다. V1은 S3 저장과 AI 요약을 동기 처리한 뒤 응답한다. " +
            "요약 성공 시 DONE과 요약문, Bedrock 장애 시에도 등록은 유지하고 FAILED와 null 요약문을 반환한다. " +
            "필수 파일 누락·유효하지 않은 파일명·PDF가 아닌 파일·빈 파일·10MB 초과는 400(E400), 인증 정보 없음은 401(E1102)로 응답한다."
    private val deleteResumeSummary = "보관 이력서 삭제"
    private val deleteResumeDescription =
        "삭제한 이력서는 이후 룸 생성·참가 신청에서 선택되지 않으며, " +
            "이미 제출된 룸의 기록에는 영향을 주지 않는다. 같은 요청을 반복해도 성공하는 멱등 계약이다. " +
            "식별자 형식 오류는 400(E400), 존재하지 않거나 본인 소유가 아닌 이력서는 404(E1010), 인증 정보 없음은 401(E1102)로 응답한다."
    private val retrySummary = "AI 이력서 요약 재시도"
    private val retryDescription =
        "AI 요약이 FAILED인 이력서만 재시도한다. 저장된 S3 원본으로 Bedrock 요약을 동기 실행하고 DONE 또는 FAILED 결과를 반환한다. " +
            "1분을 넘긴 PROCESSING은 먼저 FAILED로 확정하며 백그라운드에서 자동 재시도하지 않는다. " +
            "이미 PROCESSING이거나 DONE인 요약은 409(E1013), 존재하지 않거나 본인 소유가 아닌 이력서는 404(E1010)로 응답한다."

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            ResumeController(),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `보관 이력서 목록은 삭제한 항목을 제외하고 AI 요약 완료와 준비 중 상태를 함께 반환한다`() {
        mockMvc.perform(get("/v1/members/me/resumes").principal(principal))
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"maxCount\":10")
                    .contains("\"resumeId\":\"$defaultResumeId\"")
                    .contains("\"isDefault\":true")
                    .contains("\"status\":\"DONE\"")
                    .contains("\"status\":\"PROCESSING\",\"text\":null")
                    .doesNotContain(deletedResumeId.toString())
            }
            .andDo(
                documentApi(
                    "resumes",
                    resumesSummary,
                    resumesDescription,
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.maxCount").type(JsonFieldType.NUMBER).description("최대 보관 가능 개수 (10)"),
                        fieldWithPath("data.resumes").type(JsonFieldType.ARRAY).description("삭제되지 않은 보관 이력서 목록 (기본 이력서 우선)"),
                        *resumeFields("data.resumes[]").toTypedArray(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `AI 요약 준비 중인 이력서를 단건 조회한다`() {
        mockMvc.perform(get("/v1/members/me/resumes/{resumeId}", processingResumeId).principal(principal))
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"resumeId\":\"$processingResumeId\"")
                    .contains("\"status\":\"PROCESSING\",\"text\":null")
            }
            .andDo(
                documentApi(
                    "resume",
                    resumeSummary,
                    resumeDescription,
                    resumePathParameters(),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        *resumeFields("data").toTypedArray(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `이력서를 등록하면 완료된 AI 요약과 함께 반환한다`() {
        performCreateResume(pdfFile())
            .andExpect(status().isCreated)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"name\":\"backend-resume.pdf\"")
                    .contains("\"status\":\"DONE\"")
                    .contains("\"text\":\"Kotlin·Spring 기반 백엔드 개발 경험")
            }
            .andDo(
                documentApi(
                    "createResume",
                    createResumeSummary,
                    createResumeDescription,
                    requestParts(
                        partWithName("file").description("PDF 이력서 파일 (application/pdf, 1byte~10MB)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        *resumeFields("data").toTypedArray(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `PDF가 아닌 파일은 E400 을 반환한다`() {
        val textFile = MockMultipartFile("file", "resume.txt", MediaType.TEXT_PLAIN_VALUE, "resume".toByteArray())

        performCreateResume(textFile)
            .andExpect(status().isBadRequest)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E400\"")
            }
            .andDo(
                documentApi(
                    "createResume-e400-file-type",
                    createResumeSummary,
                    createResumeDescription,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `PDF로 위장한 파일은 E400 을 반환한다`() {
        val disguisedFile = MockMultipartFile(
            "file",
            "resume.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "plain text resume".toByteArray(),
        )

        performCreateResume(disguisedFile)
            .andExpect(status().isBadRequest)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E400\"")
            }
    }

    @Test
    fun `10MB를 초과한 파일은 E400 을 반환한다`() {
        val oversizedPdf = MockMultipartFile(
            "file",
            "resume.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            ByteArray(MAX_FILE_SIZE_BYTES + 1),
        )

        performCreateResume(oversizedPdf)
            .andExpect(status().isBadRequest)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E400\"")
            }
            .andDo(
                documentApi(
                    "createResume-e400-file-size",
                    createResumeSummary,
                    createResumeDescription,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `10MB 파일은 등록할 수 있다`() {
        val maximumSizePdf = MockMultipartFile(
            "file",
            "resume.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            pdfContent(MAX_FILE_SIZE_BYTES),
        )

        performCreateResume(maximumSizePdf)
            .andExpect(status().isCreated)
    }

    @Test
    fun `실패한 AI 요약을 재시도하면 완료된 요약을 반환한다`() {
        mockMvc.perform(
            post("/v1/members/me/resumes/{resumeId}/summary-retries", failedResumeId)
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"resumeId\":\"$failedResumeId\"")
                    .contains("\"status\":\"DONE\"")
            }
            .andDo(
                documentApi(
                    "retryResumeSummary",
                    retrySummary,
                    retryDescription,
                    resumePathParameters(),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        *resumeFields("data").toTypedArray(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `처리 중인 AI 요약은 재시도할 수 없다`() {
        mockMvc.perform(
            post("/v1/members/me/resumes/{resumeId}/summary-retries", processingResumeId)
                .principal(principal),
        )
            .andExpect(status().isConflict)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1013\"")
            }
            .andDo(
                documentApi(
                    "retryResumeSummary-e1013",
                    retrySummary,
                    retryDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `필수 파일 파트가 없으면 E400 을 반환한다`() {
        mockMvc.perform(
            multipart("/v1/members/me/resumes")
                .principal(principal),
        )
            .andExpect(status().isBadRequest)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"code\":\"E400\"")
                    .contains("\"file\"")
            }
            .andDo(
                documentApi(
                    "createResume-e400-missing-file",
                    createResumeSummary,
                    createResumeDescription,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `보관 이력서 삭제를 반복해도 성공한다`() {
        mockMvc.perform(delete("/v1/members/me/resumes/{resumeId}", deletedResumeId).principal(principal))
            .andExpect(status().isOk)

        mockMvc.perform(delete("/v1/members/me/resumes/{resumeId}", deletedResumeId).principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "deleteResume",
                    deleteResumeSummary,
                    deleteResumeDescription,
                    resumePathParameters(),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `기본 이력서도 삭제할 수 있다`() {
        mockMvc.perform(delete("/v1/members/me/resumes/{resumeId}", defaultResumeId).principal(principal))
            .andExpect(status().isOk)
    }

    @Test
    fun `존재하지 않는 이력서를 삭제하면 E1010 을 반환한다`() {
        mockMvc.perform(delete("/v1/members/me/resumes/{resumeId}", unknownResumeId).principal(principal))
            .andExpect(status().isNotFound)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1010\"")
            }
            .andDo(
                documentApi(
                    "deleteResume-e1010",
                    deleteResumeSummary,
                    deleteResumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `존재하지 않는 이력서를 단건 조회하면 E1010 을 반환한다`() {
        mockMvc.perform(get("/v1/members/me/resumes/{resumeId}", unknownResumeId).principal(principal))
            .andExpect(status().isNotFound)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1010\"")
            }
            .andDo(
                documentApi(
                    "resume-e1010",
                    resumeSummary,
                    resumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `이력서 식별자가 UUID 형식이 아니면 E400 을 반환한다`() {
        mockMvc.perform(delete("/v1/members/me/resumes/{resumeId}", "not-a-uuid").principal(principal))
            .andExpect(status().isBadRequest)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E400\"")
            }
            .andDo(
                documentApi(
                    "deleteResume-e400",
                    deleteResumeSummary,
                    deleteResumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `단건 조회의 이력서 식별자가 UUID 형식이 아니면 E400 을 반환한다`() {
        mockMvc.perform(get("/v1/members/me/resumes/{resumeId}", "not-a-uuid").principal(principal))
            .andExpect(status().isBadRequest)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E400\"")
            }
            .andDo(
                documentApi(
                    "resume-e400",
                    resumeSummary,
                    resumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `보관 이력서 목록은 인증 정보가 없으면 E1102 를 반환한다`() {
        mockMvc.perform(get("/v1/members/me/resumes"))
            .andExpect(status().isUnauthorized)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1102\"")
            }
            .andDo(documentApi("resumes-e1102", resumesSummary, resumesDescription, errorResponseFields()))
    }

    @Test
    fun `이력서 등록은 인증 정보가 없으면 E1102 를 반환한다`() {
        performCreateResume(pdfFile(), withPrincipal = false)
            .andExpect(status().isUnauthorized)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1102\"")
            }
            .andDo(documentApi("createResume-e1102", createResumeSummary, createResumeDescription, errorResponseFields()))
    }

    @Test
    fun `이력서 단건 조회는 인증 정보가 없으면 E1102 를 반환한다`() {
        mockMvc.perform(get("/v1/members/me/resumes/{resumeId}", processingResumeId))
            .andExpect(status().isUnauthorized)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1102\"")
            }
            .andDo(
                documentApi(
                    "resume-e1102",
                    resumeSummary,
                    resumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `이력서 삭제는 인증 정보가 없으면 E1102 를 반환한다`() {
        mockMvc.perform(delete("/v1/members/me/resumes/{resumeId}", deletedResumeId))
            .andExpect(status().isUnauthorized)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1102\"")
            }
            .andDo(
                documentApi(
                    "deleteResume-e1102",
                    deleteResumeSummary,
                    deleteResumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    private fun performCreateResume(
        file: MockMultipartFile,
        withPrincipal: Boolean = true,
    ) = mockMvc.perform(
        multipart("/v1/members/me/resumes")
            .file(file)
            .apply {
                if (withPrincipal) {
                    principal(principal)
                }
            },
    )

    private fun pdfFile(): MockMultipartFile {
        return MockMultipartFile(
            "file",
            "backend-resume.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "%PDF-1.7 mock resume".toByteArray(),
        )
    }

    private fun pdfContent(size: Int): ByteArray {
        return ByteArray(size).also { PDF_SIGNATURE.copyInto(it) }
    }

    private fun resumePathParameters() = pathParameters(
        parameterWithName("resumeId").description("이력서 식별자 (UUID)"),
    )

    private fun resumeFields(path: String): List<FieldDescriptor> = listOf(
        fieldWithPath("$path.resumeId").type(JsonFieldType.STRING).description("이력서 식별자 (UUID, 다른 화면에서 선택할 때 사용)"),
        fieldWithPath("$path.name").type(JsonFieldType.STRING).description("업로드 당시 원본 파일명과 동일한 이력서 이름"),
        fieldWithPath("$path.file.originalName").type(JsonFieldType.STRING).description("업로드 당시 원본 파일명"),
        fieldWithPath("$path.file.sizeBytes").type(JsonFieldType.NUMBER).description("파일 크기 (byte)"),
        fieldWithPath("$path.file.contentType").type(JsonFieldType.STRING).description("파일 미디어 타입 (application/pdf)"),
        fieldWithPath("$path.aiSummary.status").type(JsonFieldType.STRING).description(
            "AI 요약 상태 (PROCESSING | DONE | FAILED). PROCESSING이면 단건 조회를 폴링하고 최대 1분 뒤 DONE·FAILED에서 중단",
        ),
        fieldWithPath("$path.aiSummary.text").type(JsonFieldType.STRING).optional().description("AI 요약 본문 (준비 중·실패면 null)"),
        fieldWithPath("$path.isDefault").type(JsonFieldType.BOOLEAN).description("기본 이력서 여부"),
        fieldWithPath("$path.registeredAt").type(JsonFieldType.STRING).description("등록 시각 (yyyy-MM-ddTHH:mm:ss)"),
    )
}

private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024
private val PDF_SIGNATURE = "%PDF-".toByteArray()
