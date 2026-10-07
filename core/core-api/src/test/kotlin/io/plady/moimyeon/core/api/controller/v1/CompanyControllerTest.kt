package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class CompanyControllerTest : RestDocsTest() {
    private lateinit var companyService: CompanyService

    private val searchSummary = "회사 검색"
    private val searchDescription =
        "관심 회사 태그 입력의 검색 소스. 정규화된 회사명의 접두 일치로 검증 완료된 유효(미폐기) 회사를 최대 20건 반환한다. " +
            "접두로 찾지 못하면 부분 일치로 다시 찾는다(예: '올리브영' 으로 'CJ올리브영'). " +
            "타이핑 중 호출을 전제로 하므로 빈 검색어는 빈 배열 200 이고, 50자 초과만 400(E400)이다."

    @BeforeEach
    fun setUp() {
        companyService = mockk()
        mockMvc = mockController(
            CompanyController(companyService),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `검증 완료된 회사를 이름으로 검색한다`() {
        every { companyService.search("달빛") } returns listOf(Company(1L, "달빛페이"))

        mockMvc.perform(get("/v1/companies").param("query", "달빛"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "searchCompanies",
                    searchSummary,
                    searchDescription,
                    queryParameters(
                        parameterWithName("query").optional().description("검색어 (정규화 회사명 접두 일치, 최대 50자. 비면 빈 배열)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.companies").type(JsonFieldType.ARRAY).description("검색 결과 (최대 20건)"),
                        fieldWithPath("data.companies[].companyId").type(JsonFieldType.NUMBER).description("회사 id (관심 회사 저장에 사용)"),
                        fieldWithPath("data.companies[].name").type(JsonFieldType.STRING).description("회사명"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `검색어가 비어 있으면 빈 배열과 함께 200 을 반환한다`() {
        every { companyService.search("") } returns emptyList()

        mockMvc.perform(get("/v1/companies"))
            .andExpect(status().isOk)
    }

    @Test
    fun `검색어가 50자를 초과하면 E400 을 반환한다`() {
        mockMvc.perform(get("/v1/companies").param("query", "가".repeat(51)))
            .andExpect(status().isBadRequest)
            .andDo(documentApi("searchCompanies-e400-length", searchSummary, searchDescription, errorResponseFields()))
    }
}
