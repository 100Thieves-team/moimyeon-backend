package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.auth.DevAccessTokenIssuer
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.IssueDevSessionRequest
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class DevAuthControllerTest : RestDocsTest() {
    private val issuer = mockk<DevAccessTokenIssuer>()
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val summary = "개발 환경 액세스 토큰 발급"
    private val description =
        "local·local-dev·dev 프로파일에서 기존 회원 UUID로 Google OAuth를 거치지 않고 만료 없는 액세스 토큰을 응답한다. " +
            "회원이 없거나 탈퇴했으면 404(E1006), 요청 본문 형식이 잘못됐으면 400(E400)으로 응답한다."

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            DevAuthController(issuer),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `dev 회원의 만료 없는 액세스 토큰을 응답하고 쿠키는 발급하지 않는다`() {
        every { issuer.issue(memberId) } returns "access-token"

        mockMvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(IssueDevSessionRequest(memberId))),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"accessToken\":\"access-token\"") }
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andDo(
                documentApi(
                    "issueDevSession",
                    summary,
                    description,
                    requestFields(
                        fieldWithPath("memberId").type(JsonFieldType.STRING)
                            .description("개발 환경에서 로그인할 기존 회원 UUID"),
                    ),
                    successResponseFields(
                        fieldWithPath("data.accessToken").type(JsonFieldType.STRING)
                            .description("만료 시각이 없는 개발용 액세스 토큰"),
                    ),
                ),
            )

        verify(exactly = 1) { issuer.issue(memberId) }
    }

    @Test
    fun `없는 회원으로 dev 세션을 요청하면 E1006 을 응답한다`() {
        every { issuer.issue(memberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        mockMvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(IssueDevSessionRequest(memberId))),
        )
            .andExpect(status().isNotFound)
            .andDo(documentApi("issueDevSession-e1006", summary, description, errorResponseFields()))
    }

    @Test
    fun `UUID 형식이 아닌 회원 id로 dev 세션을 요청하면 E400 을 응답한다`() {
        mockMvc.perform(
            post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId":"not-a-uuid"}"""),
        )
            .andExpect(status().isBadRequest)
            .andDo(documentApi("issueDevSession-e400", summary, description, errorResponseFields()))

        verify(exactly = 0) { issuer.issue(any()) }
    }
}

private const val PATH = "/v1/auth/dev-sessions"
