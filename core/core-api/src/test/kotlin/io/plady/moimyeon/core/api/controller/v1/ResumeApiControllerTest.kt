package io.plady.moimyeon.core.api.controller.v1

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.resume.ResumeService
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class ResumeApiControllerTest : RestDocsTest() {
    private val memberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val missingMemberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000404")
    private val principal = Principal { memberId.toString() }
    private val missingMemberPrincipal = Principal { missingMemberId.toString() }
    private val defaultResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000101")
    private val readyResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000102")
    private val processingResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000103")
    private val unknownResumeId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000404")
    private val resumeService = mockk<ResumeService>()

    private val makeDefaultResumeSummary = "기본 이력서 지정"
    private val makeDefaultResumeDescription =
        "인증 회원이 소유한 AI 요약 완료 이력서를 기본 이력서로 지정한다. 기존 기본 이력서는 함께 해제되며 " +
            "이미 기본인 이력서를 다시 지정해도 성공하는 멱등 계약이다. 성공 응답에는 별도 데이터가 없으므로 클라이언트는 " +
            "로컬 목록을 갱신하거나 목록 API를 다시 조회한다. 식별자 형식 오류는 400(E400), 인증 정보 없음은 401(E1102), " +
            "탈퇴 등으로 회원이 존재하지 않으면 404(E1006), 존재하지 않거나 본인 소유가 아닌 이력서는 404(E1010), " +
            "AI 요약이 완료되지 않은 이력서는 409(E1012)로 응답한다."

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            ResumeApiController(resumeService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `AI 요약이 완료된 이력서를 기본으로 지정한다`() {
        every { resumeService.makeDefault(memberId, readyResumeId) } just Runs

        mockMvc.perform(
            post("/v1/members/me/resumes/{resumeId}/make-default", readyResumeId)
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "makeResumeDefault",
                    makeDefaultResumeSummary,
                    makeDefaultResumeDescription,
                    resumePathParameters(),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )

        verify(exactly = 1) { resumeService.makeDefault(memberId, readyResumeId) }
    }

    @Test
    fun `이미 기본인 이력서를 다시 지정해도 성공한다`() {
        every { resumeService.makeDefault(memberId, defaultResumeId) } just Runs

        mockMvc.perform(
            post("/v1/members/me/resumes/{resumeId}/make-default", defaultResumeId)
                .principal(principal),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `기본 지정의 이력서 식별자가 UUID 형식이 아니면 E400 을 반환한다`() {
        mockMvc.perform(
            post("/v1/members/me/resumes/{resumeId}/make-default", "not-a-uuid")
                .principal(principal),
        )
            .andExpect(status().isBadRequest)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E400\"")
            }
            .andDo(
                documentApi(
                    "makeResumeDefault-e400",
                    makeDefaultResumeSummary,
                    makeDefaultResumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `존재하지 않는 이력서는 기본으로 지정할 수 없어 E1010 을 반환한다`() {
        every {
            resumeService.makeDefault(memberId, unknownResumeId)
        } throws CoreException(CoreErrorType.RESUME_NOT_FOUND)

        mockMvc.perform(
            post("/v1/members/me/resumes/{resumeId}/make-default", unknownResumeId)
                .principal(principal),
        )
            .andExpect(status().isNotFound)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1010\"")
            }
            .andDo(
                documentApi(
                    "makeResumeDefault-e1010",
                    makeDefaultResumeSummary,
                    makeDefaultResumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `존재하지 않는 회원은 기본 이력서를 지정할 수 없어 E1006 을 반환한다`() {
        every {
            resumeService.makeDefault(missingMemberId, readyResumeId)
        } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        mockMvc.perform(
            post("/v1/members/me/resumes/{resumeId}/make-default", readyResumeId)
                .principal(missingMemberPrincipal),
        )
            .andExpect(status().isNotFound)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1006\"")
            }
            .andDo(
                documentApi(
                    "makeResumeDefault-e1006",
                    makeDefaultResumeSummary,
                    makeDefaultResumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `AI 요약이 완료되지 않은 이력서는 기본으로 지정할 수 없어 E1012 를 반환한다`() {
        every {
            resumeService.makeDefault(memberId, processingResumeId)
        } throws CoreException(CoreErrorType.RESUME_NOT_READY)

        mockMvc.perform(
            post("/v1/members/me/resumes/{resumeId}/make-default", processingResumeId)
                .principal(principal),
        )
            .andExpect(status().isConflict)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1012\"")
            }
            .andDo(
                documentApi(
                    "makeResumeDefault-e1012",
                    makeDefaultResumeSummary,
                    makeDefaultResumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `기본 이력서 지정은 인증 정보가 없으면 E1102 를 반환한다`() {
        mockMvc.perform(post("/v1/members/me/resumes/{resumeId}/make-default", readyResumeId))
            .andExpect(status().isUnauthorized)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1102\"")
            }
            .andDo(
                documentApi(
                    "makeResumeDefault-e1102",
                    makeDefaultResumeSummary,
                    makeDefaultResumeDescription,
                    resumePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    private fun resumePathParameters() = pathParameters(
        parameterWithName("resumeId").description("이력서 식별자 (UUID)"),
    )
}
