package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.domain.terms.Terms
import io.plady.moimyeon.core.domain.terms.TermsService
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.enums.TermsType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID

class TermsControllerTest : RestDocsTest() {
    private lateinit var termsService: TermsService
    private val listSummary = "현재 유효 약관 목록 조회"
    private val listDescription =
        "비로그인으로 종류별 최신 시행 문서를 본문 포함으로 조회한다. ACTIVE·미삭제 문서 중 한국 시각(Asia/Seoul)으로 시행일이 된 " +
            "문서를 선택하며 SERVICE, PRIVACY 순서로 반환한다. DRAFT와 미래 시행 문서는 제외한다. " +
            "필요한 문서 종류가 없거나 같은 종류의 최신 시행시각이 중복되면 503(E1203)을 반환한다."
    private val detailSummary = "약관 버전 상세 조회"
    private val detailDescription =
        "비로그인으로 termsId에 해당하는 시행된 현재·과거 버전(ACTIVE·DEPRECATED)의 본문을 조회한다. " +
            "DRAFT·미시행·삭제·없는 문서는 404(E1202), UUID 형식 오류는 400(E400)을 반환한다."
    private val terms = Terms(
        id = UUID.fromString("019daf00-0000-7000-8000-000000000001"),
        type = TermsType.SERVICE,
        version = "v1.0",
        title = "모이면 이용약관",
        content = "제1조(목적) 이 약관은 모이면 서비스의 이용 조건과 절차를 규정합니다.",
        required = true,
        effectiveFrom = LocalDateTime.of(2026, 7, 1, 0, 0),
        status = TermsStatus.ACTIVE,
    )

    @BeforeEach
    fun setUp() {
        termsService = mockk()
        mockMvc = mockController(TermsController(termsService), controllerAdvice = ApiControllerAdvice())
    }

    @Test
    fun termsList() {
        every { termsService.getActiveTerms() } returns listOf(
            terms,
            terms.copy(
                id = UUID.fromString("019daf00-0000-7000-8000-000000000002"),
                type = TermsType.PRIVACY,
                title = "개인정보 처리방침",
                content = "모이면은 회원 가입과 서비스 제공을 위해 최소한의 개인정보를 수집·이용합니다.",
            ),
        )

        mockMvc.perform(get("/v1/terms"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "termsList",
                    listSummary,
                    listDescription,
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.terms").type(JsonFieldType.ARRAY).description("종류별 현재 유효한 약관 목록"),
                        *termsFields("data.terms[]"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `현재 약관 미설정이나 시행시각 충돌은 E1203으로 응답한다`() {
        every { termsService.getActiveTerms() } throws CoreException(CoreErrorType.TERMS_UNAVAILABLE)

        mockMvc.perform(get("/v1/terms"))
            .andExpect(status().isServiceUnavailable)
            .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("error").path("code").asString()).isEqualTo("E1203") }
            .andDo(documentApi("termsList-e1203", listSummary, listDescription, errorResponseFields()))
    }

    @Test
    fun `시행된 과거 약관의 본문을 ID로 조회한다`() {
        every { termsService.getTerms(terms.id) } returns terms.copy(status = TermsStatus.DEPRECATED)

        mockMvc.perform(get("/v1/terms/{termsId}", terms.id))
            .andExpect(status().isOk)
            .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("data").path("content").asString()).isEqualTo(terms.content) }
            .andDo(
                documentApi(
                    "termsDetail",
                    detailSummary,
                    detailDescription,
                    pathParameters(parameterWithName("termsId").description("약관 버전 식별자 (UUID)")),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        *termsFields("data"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `공개할 수 없는 문서는 E1202로 응답한다`() {
        every { termsService.getTerms(terms.id) } throws CoreException(CoreErrorType.TERMS_NOT_FOUND)

        mockMvc.perform(get("/v1/terms/{termsId}", terms.id))
            .andExpect(status().isNotFound)
            .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("error").path("code").asString()).isEqualTo("E1202") }
            .andDo(
                documentApi(
                    "termsDetail-e1202",
                    detailSummary,
                    detailDescription,
                    pathParameters(parameterWithName("termsId").description("약관 버전 식별자 (UUID)")),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `잘못된 문서 UUID는 E400으로 응답한다`() {
        mockMvc.perform(get("/v1/terms/{termsId}", "invalid-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(JsonMapper.shared().readTree(it.response.contentAsByteArray).path("error").path("code").asString()).isEqualTo("E400") }
            .andDo(
                documentApi(
                    "termsDetail-e400",
                    detailSummary,
                    detailDescription,
                    pathParameters(parameterWithName("termsId").description("약관 버전 식별자 (UUID)")),
                    errorResponseFields(),
                ),
            )
    }

    private fun termsFields(prefix: String): Array<FieldDescriptor> = arrayOf(
        fieldWithPath("$prefix.termsId").type(JsonFieldType.STRING).description("약관 버전 식별자 (UUID)"),
        fieldWithPath("$prefix.type").type(JsonFieldType.STRING).description("약관 종류 (SERVICE | PRIVACY)"),
        fieldWithPath("$prefix.version").type(JsonFieldType.STRING).description("약관 버전"),
        fieldWithPath("$prefix.title").type(JsonFieldType.STRING).description("약관 제목"),
        fieldWithPath("$prefix.content").type(JsonFieldType.STRING).description("약관 전문 (Markdown, HTML 출력 시 안전한 렌더링 필요)"),
        fieldWithPath("$prefix.required").type(JsonFieldType.BOOLEAN).description("가입 필수 동의 여부"),
        fieldWithPath("$prefix.effectiveFrom").type(JsonFieldType.STRING).description("한국 시각(Asia/Seoul) 기준 시행일시"),
    )
}
