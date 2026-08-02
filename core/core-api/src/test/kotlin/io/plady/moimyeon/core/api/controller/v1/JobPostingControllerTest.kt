package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.domain.jobposting.JobPosting
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class JobPostingControllerTest : RestDocsTest() {
    private lateinit var jobPostingService: JobPostingService

    private val jobPostingsSummary = "회사별 채용 공고 목록 조회"
    private val jobPostingsDescription =
        "기본 정보(「룸 생성」 §4.1)에서 회사를 고르면 그 회사에 속한 활성 공고 목록이 최신순 최대 20건 채워진다. " +
            "공고명(query)으로 부분 검색할 수 있고, 공고를 고르면 회사가 확정되며 대표 직무로 직무 셀렉트가 자동 채워질 수 있다. " +
            "미검증 공고(verified=false)도 목록에는 포함된다(탐색 필터에서만 숨김, BE-03). query 가 50자를 초과하면 400(E400)."

    @BeforeEach
    fun setUp() {
        jobPostingService = mockk()
        mockMvc = mockController(
            JobPostingController(jobPostingService),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `회사에 속한 공고를 조회한다`() {
        every { jobPostingService.search(43429L, "개발") } returns listOf(
            JobPosting(
                id = 2029L,
                companyId = 43429L,
                postingName = "프론트엔드 개발자 (결제플랫폼)",
                jobRoleId = 1L,
                jobRoleName = "프론트엔드 개발",
                sourceUrl = "https://dalbitpay.example.com/careers/fe-pay",
                verified = true,
            ),
            JobPosting(
                id = 2036L,
                companyId = 43429L,
                postingName = "백엔드 개발자 (정산)",
                jobRoleId = null,
                jobRoleName = null,
                sourceUrl = null,
                verified = false,
            ),
        )

        mockMvc.perform(get("/v1/companies/{companyId}/job-postings", 43429L).param("query", "개발"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "jobPostings",
                    jobPostingsSummary,
                    jobPostingsDescription,
                    pathParameters(
                        parameterWithName("companyId").description("공고를 조회할 회사 id (/v1/companies 검색 결과)"),
                    ),
                    queryParameters(
                        parameterWithName("query").optional().description("공고명 부분 일치 검색어 (선택, 최대 50자, 없으면 회사 전체 공고)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.jobPostings").type(JsonFieldType.ARRAY).description("채용 공고 목록 (최대 20건)"),
                        fieldWithPath("data.jobPostings[].jobPostingId").type(JsonFieldType.NUMBER).description("채용 공고 id (룸 생성에 사용)"),
                        fieldWithPath("data.jobPostings[].companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.jobPostings[].postingName").type(JsonFieldType.STRING).description("공고명"),
                        fieldWithPath("data.jobPostings[].jobRoleId").type(JsonFieldType.NUMBER).optional()
                            .description("대표 직무 id (선택, 직무 셀렉트 자동 채움)"),
                        fieldWithPath("data.jobPostings[].jobRoleName").type(JsonFieldType.STRING).optional().description("대표 직무명 (선택)"),
                        fieldWithPath("data.jobPostings[].sourceUrl").type(JsonFieldType.STRING).optional().description("원본 공고 링크 (선택)"),
                        fieldWithPath("data.jobPostings[].verified").type(JsonFieldType.BOOLEAN).description("운영 검수 여부 (링크 생성분은 false)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `검색어 없이 회사 전체 공고를 조회한다`() {
        every { jobPostingService.search(43429L, "") } returns emptyList()

        mockMvc.perform(get("/v1/companies/{companyId}/job-postings", 43429L))
            .andExpect(status().isOk)
    }

    @Test
    fun `검색어가 50자를 초과하면 E400 을 반환한다`() {
        mockMvc.perform(get("/v1/companies/{companyId}/job-postings", 43429L).param("query", "가".repeat(51)))
            .andExpect(status().isBadRequest)
            .andDo(documentApi("jobPostings-e400-length", jobPostingsSummary, jobPostingsDescription, errorResponseFields()))
    }
}
