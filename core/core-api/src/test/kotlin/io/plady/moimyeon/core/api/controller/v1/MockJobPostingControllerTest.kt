package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.CreateJobPostingRequest
import io.plady.moimyeon.core.api.controller.v1.request.JobPostingLinkMetadataRequest
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class MockJobPostingControllerTest : RestDocsTest() {
    private val linkMetadataSummary = "채용 공고 링크 메타데이터 조회"
    private val linkMetadataDescription =
        "목록에 없는 공고를 만들기 전, 링크의 OG 태그를 읽어 공고명 후보와 미리보기(이미지·설명·출처)를 돌려준다(§4.1). " +
            "회사는 OG 에서 추출하지 않으며, 생성 요청에서 목록으로 지정한다. url 누락·형식 오류는 400(E400)으로 응답한다. " +
            "(모킹: 링크와 무관하게 고정 메타를 반환하되 요청 url 을 출처로 반영한다. BE-03/MOI-334 에서 실구현)"
    private val createSummary = "채용 공고 즉시 생성"
    private val createDescription =
        "링크로 공고를 즉시 생성한다(§4.1). 승인 대기 없이 verified=false 로 만들어져 바로 룸 생성에 사용할 수 있다. " +
            "회사는 기존 카탈로그 회사 id 로 받고, 공고명은 사용자가 확정한 값을 사용한다. " +
            "companyId·url·postingName 누락·형식 오류는 400(E400)으로 응답한다. " +
            "(모킹: 고정 jobPostingId(90101)를 반환한다. BE-03/MOI-334 에서 실구현)"

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            MockJobPostingController(),
            controllerAdvice = ApiControllerAdvice(),
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
