package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.SubmitJobPostingLinkRequest
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class JobPostingControllerTest : RestDocsTest() {
    private val jobPostingsSummary = "회사별 채용 공고 목록 조회"
    private val jobPostingsDescription =
        "면접 정보(B·06)에서 회사를 고르면 그 회사의 채용 공고 목록이 채워진다. 공고를 고르면 직무 셀렉트가 자동으로 채워질 수 있다. " +
            "(모킹: '공고' 카탈로그 도입이 미확정이라 회사와 무관하게 고정 3건을 반환하되 요청 companyId 를 각 공고에 반영한다)"
    private val requestSummary = "채용 공고 링크 추가 요청"
    private val requestDescription =
        "'찾는 공고가 없나요? 공고 링크를 남기면 추가해 드려요'(B·06, §4.1) — 목록에 없는 공고를 링크로 접수한다. " +
            "회사 미선택 상태에서도 요청할 수 있다. url 누락·형식 오류는 400(E400)으로 응답한다. " +
            "(모킹: 접수(RECEIVED)만 반환하고 고정 requestId(5001)를 돌려준다)"

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            JobPostingController(),
            controllerAdvice = ApiControllerAdvice(),
            validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() },
        )
    }

    @Test
    fun jobPostings() {
        mockMvc.perform(get("/v1/companies/{companyId}/job-postings", 43429L))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "jobPostings",
                    jobPostingsSummary,
                    jobPostingsDescription,
                    pathParameters(
                        parameterWithName("companyId").description("공고를 조회할 회사 id (/v1/companies 검색 결과)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.jobPostings").type(JsonFieldType.ARRAY).description("채용 공고 목록"),
                        fieldWithPath("data.jobPostings[].jobPostingId").type(JsonFieldType.NUMBER).description("채용 공고 id"),
                        fieldWithPath("data.jobPostings[].companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.jobPostings[].title").type(JsonFieldType.STRING).description("공고 제목"),
                        fieldWithPath("data.jobPostings[].jobRoleId").type(JsonFieldType.NUMBER).optional()
                            .description("매핑된 직무 id (선택, 직무 셀렉트 자동 채움)"),
                        fieldWithPath("data.jobPostings[].jobRoleName").type(JsonFieldType.STRING).optional().description("매핑된 직무명 (선택)"),
                        fieldWithPath("data.jobPostings[].sourceUrl").type(JsonFieldType.STRING).optional().description("원본 공고 링크 (선택)"),
                        fieldWithPath("data.jobPostings[].status").type(JsonFieldType.STRING).description("공고 상태 (OPEN)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun requestJobPosting() {
        val request = SubmitJobPostingLinkRequest(
            companyId = 43429L,
            url = "https://company.example.com/careers/12345",
            memo = "결제플랫폼 프론트엔드 공고예요.",
        )

        mockMvc.perform(
            post("/v1/job-posting-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "requestJobPosting",
                    requestSummary,
                    requestDescription,
                    requestFields(
                        fieldWithPath("companyId").type(JsonFieldType.NUMBER).optional().description("회사 id (선택, 미선택 상태에서도 요청 가능)"),
                        fieldWithPath("url").type(JsonFieldType.STRING).description("추가를 요청하는 공고 링크 (필수, 최대 2000자)"),
                        fieldWithPath("memo").type(JsonFieldType.STRING).optional().description("운영 전달용 메모 (선택, 최대 500자)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.requestId").type(JsonFieldType.NUMBER).description("접수된 요청 id"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("접수 상태 (RECEIVED)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
