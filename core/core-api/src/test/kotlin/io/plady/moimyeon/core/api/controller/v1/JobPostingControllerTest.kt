package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.CreateJobPostingRequest
import io.plady.moimyeon.core.api.controller.v1.request.JobPostingLinkMetadataRequest
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

class JobPostingControllerTest : RestDocsTest() {
    private val jobPostingsSummary = "회사별 채용 공고 목록 조회"
    private val jobPostingsDescription =
        "기본 정보(「룸 생성」 §4.1)에서 회사를 고르면 그 회사의 채용 공고 목록이 채워진다. 공고를 고르면 직무 셀렉트가 자동으로 채워질 수 있다. " +
            "(모킹: '공고' 카탈로그 시드가 미구현이라 회사와 무관하게 고정 3건을 반환하되 요청 companyId 를 각 공고에 반영한다)"
    private val linkMetadataSummary = "채용 공고 링크 메타데이터 조회"
    private val linkMetadataDescription =
        "목록에 없는 공고를 만들기 전, 링크의 OG 태그를 읽어 공고명 후보와 미리보기(이미지·설명·출처)를 돌려준다(§4.1). " +
            "회사는 OG 에서 추출하지 않으며, 생성 요청에서 목록으로 지정한다. url 누락·형식 오류는 400(E400)으로 응답한다. " +
            "(모킹: 링크와 무관하게 고정 메타를 반환하되 요청 url 을 출처로 반영한다)"
    private val createSummary = "채용 공고 즉시 생성"
    private val createDescription =
        "링크로 공고를 즉시 생성한다(§4.1). 승인 대기 없이 verified=false 로 만들어져 바로 룸 생성에 사용할 수 있다. " +
            "회사는 기존 카탈로그 회사 id 로 받고, 공고명은 사용자가 확정한 값을 사용한다. " +
            "companyId·url·postingName 누락·형식 오류는 400(E400)으로 응답한다. " +
            "(모킹: 고정 jobPostingId(90101)를 반환한다)"

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            JobPostingController(),
            controllerAdvice = ApiControllerAdvice(),
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
                        fieldWithPath("data.jobPostings[].postingName").type(JsonFieldType.STRING).description("공고명"),
                        fieldWithPath("data.jobPostings[].jobRoleId").type(JsonFieldType.NUMBER).optional()
                            .description("매핑된 직무 id (선택, 직무 셀렉트 자동 채움)"),
                        fieldWithPath("data.jobPostings[].jobRoleName").type(JsonFieldType.STRING).optional().description("매핑된 직무명 (선택)"),
                        fieldWithPath("data.jobPostings[].sourceUrl").type(JsonFieldType.STRING).optional().description("원본 공고 링크 (선택)"),
                        fieldWithPath("data.jobPostings[].verified").type(JsonFieldType.BOOLEAN).description("운영 검수 여부 (링크 생성분은 false)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun jobPostingLinkMetadata() {
        val request = JobPostingLinkMetadataRequest(url = "https://company.example.com/careers/12345")

        mockMvc.perform(
            post("/v1/job-postings/link-metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "jobPostingLinkMetadata",
                    linkMetadataSummary,
                    linkMetadataDescription,
                    requestFields(
                        fieldWithPath("url").type(JsonFieldType.STRING).description("메타데이터를 읽을 공고 링크 (필수, 최대 2000자)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.postingName").type(JsonFieldType.STRING).description("공고명 후보 (og:title, 사용자가 확인·수정)"),
                        fieldWithPath("data.imageUrl").type(JsonFieldType.STRING).optional().description("미리보기 이미지 (og:image, 없으면 null)"),
                        fieldWithPath("data.description").type(JsonFieldType.STRING).optional().description("미리보기 설명 (og:description, 없으면 null)"),
                        fieldWithPath("data.sourceUrl").type(JsonFieldType.STRING).description("공고 출처 링크 (og:url, 없으면 요청 url)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun createJobPosting() {
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
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "createJobPosting",
                    createSummary,
                    createDescription,
                    requestFields(
                        fieldWithPath("companyId").type(JsonFieldType.NUMBER).description("회사 id (필수, /v1/companies 검색 결과)"),
                        fieldWithPath("url").type(JsonFieldType.STRING).description("원본 공고 링크 (필수, 최대 2000자)"),
                        fieldWithPath("postingName").type(JsonFieldType.STRING).description("확정한 공고명 (필수, 최대 100자)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.jobPostingId").type(JsonFieldType.NUMBER).description("생성된 채용 공고 id (룸 생성에 바로 사용)"),
                        fieldWithPath("data.companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.postingName").type(JsonFieldType.STRING).description("공고명"),
                        fieldWithPath("data.sourceUrl").type(JsonFieldType.STRING).description("공고 출처 링크"),
                        fieldWithPath("data.verified").type(JsonFieldType.BOOLEAN).description("운영 검수 여부 (생성 직후 false)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
