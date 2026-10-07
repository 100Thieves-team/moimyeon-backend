package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.CreateJobPostingRequest
import io.plady.moimyeon.core.api.controller.v1.request.JobPostingLinkMetadataRequest
import io.plady.moimyeon.core.api.controller.v1.response.CompanyResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingSearchItemResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobPostingSearchResponse
import io.plady.moimyeon.core.api.facade.JobPostingSearchFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.jobposting.JobPosting
import io.plady.moimyeon.core.domain.jobposting.JobPostingCreationCommand
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.domain.jobposting.LinkMetadata
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class JobPostingControllerTest : RestDocsTest() {
    private lateinit var jobPostingService: JobPostingService
    private lateinit var jobPostingSearchFacade: JobPostingSearchFacade
    private val memberId: UUID = UUID.randomUUID()
    private val principal = Principal { memberId.toString() }

    private val jobPostingsSummary = "회사별 채용 공고 목록 조회"
    private val jobPostingsDescription =
        "기본 정보(「룸 생성」 §4.1)에서 회사를 고르면 그 회사에 속한 활성 공고 목록이 최신순 최대 20건 채워진다. " +
            "공고명(query)으로 부분 검색할 수 있고, 공고를 고르면 회사가 확정되며 대표 직무로 직무 셀렉트가 자동 채워질 수 있다. " +
            "미검증 공고(verified=false)도 목록에는 포함된다(탐색 필터에서만 숨김, BE-03). query 가 50자를 초과하면 400(E400)."

    private val searchSummary = "회사·공고 통합 검색"
    private val searchDescription =
        "회사와 공고를 하나의 검색 바로 찾는다(MOI-390). 탐색 필터와 룸 생성이 같은 응답을 쓰고, 화면이 회사 행을 다르게 해석한다. " +
            "회사명이 맞아도 공고명이 맞아도 결과는 `회사 | 공고명` 형태의 공고 행으로 통일되며, 회사가 매치되면 회사 행도 함께 내려간다. " +
            "'네이버 백엔드' 처럼 회사와 공고를 함께 친 검색어는 회사로 좁힌 뒤 나머지로 공고를 거른다. " +
            "회사가 잡히지 않으면 공고명에서 토큰을 AND 로 찾는다(어순 무관). " +
            "미검증 공고와 룸이 없는 공고도 포함되고, 결과 수 상한은 서버가 고정한다. " +
            "타이핑 중 호출을 전제로 하므로 빈 입력·최소 길이 미만은 400 이 아니라 빈 배열 200 이며, 50자 초과만 400(E400)이다. " +
            "요청 query 를 그대로 echo 하므로 늦게 도착한 이전 응답을 클라이언트가 버릴 수 있다. " +
            "붙여 쓴 검색어('네이버백엔드')는 분해되지 않아 결과가 없을 수 있다."

    private val linkMetadataSummary = "채용 공고 링크 메타데이터 조회"
    private val linkMetadataDescription =
        "목록에 없는 공고를 만들기 전, 링크의 OG 태그를 서버가 읽어 공고명 후보와 미리보기(이미지·설명·출처)를 돌려준다(§4.1). " +
            "브라우저는 CORS 로 외부 페이지를 직접 못 읽어 서버가 대신 fetch 한다. 사용자가 고른 companyId 를 함께 받아 " +
            "'이 링크는 그 회사의 공고'라고 일단 가정하고 그대로 돌려준다(링크·회사 일치나 회사 실존은 미리보기에서 검증하지 않음, 생성에서 검증). " +
            "fetch 실패·OG 없음·봇 차단·타임아웃이면 postingName 이 null 로 내려가고, 사용자는 공고명을 직접 입력해 생성으로 넘어간다. " +
            "companyId 누락(양수 아님)·url 형식 오류(http/https 아님)는 400(E400)."
    private val createSummary = "채용 공고 즉시 생성"
    private val createDescription =
        "링크로 공고를 즉시 생성한다(§4.1). 승인 대기 없이 verified=false 로 만들어져 바로 룸 생성에 사용할 수 있다. " +
            "회사는 기존 카탈로그 회사 id 로 받고, 공고명은 사용자가 확정한 값을 쓰며, 생성자는 인증 회원으로 기록된다. " +
            "같은 URL 재요청은 새로 만들지 않고 기존 공고를 돌려준다(멱등). " +
            "companyId·url·postingName 누락·형식 오류는 400(E400), 존재하지 않는 회사는 400(E1303), 미인증은 401(E1102)."

    @BeforeEach
    fun setUp() {
        jobPostingService = mockk()
        jobPostingSearchFacade = mockk()
        mockMvc = mockController(
            JobPostingController(jobPostingService, jobPostingSearchFacade),
            LoginMemberArgumentResolver(),
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

    @Test
    fun `회사와 공고를 하나의 검색어로 찾는다`() {
        every { jobPostingSearchFacade.search("네이버 백엔드", null) } returns JobPostingSearchResponse(
            query = "네이버 백엔드",
            companies = listOf(CompanyResponse(91221L, "네이버")),
            jobPostings = listOf(
                JobPostingSearchItemResponse(
                    jobPostingId = 2029L,
                    company = CompanyResponse(91221L, "네이버"),
                    postingName = "백엔드 개발자 (정산)",
                    jobRoleId = 1L,
                    jobRoleName = "백엔드 개발",
                    sourceUrl = "https://naver.example.com/careers/be",
                    verified = true,
                ),
                JobPostingSearchItemResponse(
                    jobPostingId = 2036L,
                    company = CompanyResponse(91222L, "플레이스앤"),
                    postingName = "[네이버 계열사] 백엔드 엔지니어",
                    jobRoleId = null,
                    jobRoleName = null,
                    sourceUrl = null,
                    verified = false,
                ),
            ),
        )

        mockMvc.perform(get("/v1/job-postings/search").param("query", "네이버 백엔드"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "searchJobPostings",
                    searchSummary,
                    searchDescription,
                    queryParameters(
                        parameterWithName("query").optional().description("검색어 (최대 50자. 비거나 짧으면 빈 배열)"),
                        parameterWithName("companyId").optional().description("회사 좁히기 (선택). 주면 회사 검색을 건너뛰고 그 회사 안에서만 찾는다"),
                    ),
                    responseHeaders(
                        headerWithName("Cache-Control").description("개인화가 없는 응답이라 캐시를 허용한다 (public, max-age=60)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.query").type(JsonFieldType.STRING).description("요청 검색어 echo (stale 응답 판별용)"),
                        fieldWithPath("data.companies").type(JsonFieldType.ARRAY).description("회사 행. 화면이 '전체 보기' 또는 '좁히기'로 해석한다"),
                        fieldWithPath("data.companies[].companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.companies[].name").type(JsonFieldType.STRING).description("회사명"),
                        fieldWithPath("data.jobPostings").type(JsonFieldType.ARRAY).description("공고 행 (회사명 매치가 공고명 매치보다 앞에 온다)"),
                        fieldWithPath("data.jobPostings[].jobPostingId").type(JsonFieldType.NUMBER).description("채용 공고 id (룸 생성에 사용)"),
                        fieldWithPath("data.jobPostings[].company.companyId").type(JsonFieldType.NUMBER).description("공고가 속한 회사 id"),
                        fieldWithPath("data.jobPostings[].company.name").type(JsonFieldType.STRING).description("공고가 속한 회사명 (행 앞에 표시)"),
                        fieldWithPath("data.jobPostings[].postingName").type(JsonFieldType.STRING).description("공고명"),
                        fieldWithPath("data.jobPostings[].jobRoleId").type(JsonFieldType.NUMBER).optional().description("대표 직무 id (선택)"),
                        fieldWithPath("data.jobPostings[].jobRoleName").type(JsonFieldType.STRING).optional().description("대표 직무명 (선택)"),
                        fieldWithPath("data.jobPostings[].sourceUrl").type(JsonFieldType.STRING).optional().description("원본 공고 링크 (선택)"),
                        fieldWithPath("data.jobPostings[].verified").type(JsonFieldType.BOOLEAN).description("운영 검수 여부 (링크 생성분은 false)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `검색어가 비어 있으면 빈 배열과 함께 200 을 반환한다`() {
        every { jobPostingSearchFacade.search("", null) } returns JobPostingSearchResponse.empty("")

        mockMvc.perform(get("/v1/job-postings/search"))
            .andExpect(status().isOk)
            .andDo(documentApi("searchJobPostings-empty", searchSummary, searchDescription))
    }

    @Test
    fun `통합 검색어가 50자를 초과하면 E400 을 반환한다`() {
        mockMvc.perform(get("/v1/job-postings/search").param("query", "가".repeat(51)))
            .andExpect(status().isBadRequest)
            .andDo(documentApi("searchJobPostings-e400-length", searchSummary, searchDescription, errorResponseFields()))
    }

    @Test
    fun jobPostingLinkMetadata() {
        every { jobPostingService.fetchLinkMetadata("https://company.example.com/careers/12345") } returns LinkMetadata(
            postingName = "프론트엔드 개발자 (결제플랫폼)",
            imageUrl = "https://img.example.com/careers/fe-pay.png",
            description = "결제·정산 플랫폼 프론트엔드 개발자를 모집합니다.",
            sourceUrl = "https://company.example.com/careers/12345",
        )
        val request = JobPostingLinkMetadataRequest(companyId = 43429L, url = "https://company.example.com/careers/12345")

        mockMvc.perform(
            post("/v1/job-postings/link-metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"companyId\":43429") }
            .andDo(
                documentApi(
                    "jobPostingLinkMetadata",
                    linkMetadataSummary,
                    linkMetadataDescription,
                    requestFields(
                        fieldWithPath("companyId").type(JsonFieldType.NUMBER).description("이 링크가 속한다고 가정할 회사 id (필수, /v1/companies 검색 결과)"),
                        fieldWithPath("url").type(JsonFieldType.STRING).description("메타데이터를 읽을 공고 링크 (필수, http/https, 최대 2000자)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.companyId").type(JsonFieldType.NUMBER).description("가정한 회사 id (요청 값 echo, 생성 요청에 그대로 사용)"),
                        fieldWithPath("data.postingName").type(JsonFieldType.STRING).optional()
                            .description("공고명 후보 (og:title, 사용자가 확인·수정). fetch 실패·OG 없음이면 null → 직접 입력"),
                        fieldWithPath("data.imageUrl").type(JsonFieldType.STRING).optional().description("미리보기 이미지 (og:image, 없으면 null)"),
                        fieldWithPath("data.description").type(JsonFieldType.STRING).optional().description("미리보기 설명 (og:description, 없으면 null)"),
                        fieldWithPath("data.sourceUrl").type(JsonFieldType.STRING).description("공고 출처 링크 (og:url, 없으면 요청 url)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `링크 메타데이터 fetch 가 실패하면 postingName 이 null 로 내려간다`() {
        every { jobPostingService.fetchLinkMetadata(any()) } returns LinkMetadata(
            postingName = null,
            imageUrl = null,
            description = null,
            sourceUrl = "https://broken.example.com/404",
        )
        val request = JobPostingLinkMetadataRequest(companyId = 43429L, url = "https://broken.example.com/404")

        mockMvc.perform(
            post("/v1/job-postings/link-metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"postingName\":null") }
    }

    @Test
    fun `링크 메타데이터 url 이 http 형식이 아니면 E400`() {
        val request = JobPostingLinkMetadataRequest(companyId = 43429L, url = "ftp://company.example.com/careers")

        mockMvc.perform(
            post("/v1/job-postings/link-metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("jobPostingLinkMetadata-e400", linkMetadataSummary, linkMetadataDescription, errorResponseFields()))
    }

    @Test
    fun `링크 메타데이터 companyId 가 양수가 아니면 E400`() {
        val request = JobPostingLinkMetadataRequest(companyId = 0L, url = "https://company.example.com/careers/12345")

        mockMvc.perform(
            post("/v1/job-postings/link-metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
    }

    @Test
    fun createJobPosting() {
        every { jobPostingService.create(memberId, any<JobPostingCreationCommand>()) } returns JobPosting(
            id = 90101L,
            companyId = 43429L,
            postingName = "프론트엔드 개발자 (결제플랫폼)",
            jobRoleId = null,
            jobRoleName = null,
            sourceUrl = "https://company.example.com/careers/12345",
            verified = false,
        )
        val request = CreateJobPostingRequest(
            companyId = 43429L,
            url = "https://company.example.com/careers/12345",
            postingName = "프론트엔드 개발자 (결제플랫폼)",
        )

        mockMvc.perform(
            post("/v1/job-postings")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "createJobPosting",
                    createSummary,
                    createDescription,
                    requestFields(
                        fieldWithPath("companyId").type(JsonFieldType.NUMBER).description("회사 id (필수, /v1/companies 검색 결과)"),
                        fieldWithPath("url").type(JsonFieldType.STRING).description("원본 공고 링크 (필수, http/https, 최대 2000자)"),
                        fieldWithPath("postingName").type(JsonFieldType.STRING).description("확정한 공고명 (필수, 최대 100자)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.jobPostingId").type(JsonFieldType.NUMBER).description("생성(또는 재사용)된 채용 공고 id (룸 생성에 바로 사용)"),
                        fieldWithPath("data.companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.postingName").type(JsonFieldType.STRING).description("공고명"),
                        fieldWithPath("data.sourceUrl").type(JsonFieldType.STRING).description("공고 출처 링크"),
                        fieldWithPath("data.verified").type(JsonFieldType.BOOLEAN).description("운영 검수 여부 (생성 직후 false)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `공고 생성 시 공고명이 비면 E400`() {
        val request = CreateJobPostingRequest(
            companyId = 43429L,
            url = "https://company.example.com/careers/12345",
            postingName = "   ",
        )

        mockMvc.perform(
            post("/v1/job-postings")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("createJobPosting-e400", createSummary, createDescription, errorResponseFields()))
    }

    @Test
    fun `공고 생성 시 인증이 없으면 E1102`() {
        val request = CreateJobPostingRequest(
            companyId = 43429L,
            url = "https://company.example.com/careers/12345",
            postingName = "프론트엔드 개발자 (결제플랫폼)",
        )

        mockMvc.perform(
            post("/v1/job-postings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isUnauthorized)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1102\"") }
            .andDo(documentApi("createJobPosting-e1102", createSummary, createDescription, errorResponseFields()))
    }
}
