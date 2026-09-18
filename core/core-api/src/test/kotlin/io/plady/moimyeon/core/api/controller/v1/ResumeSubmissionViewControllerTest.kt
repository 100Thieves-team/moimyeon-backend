package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.room.ResumeOriginalViewService
import io.plady.moimyeon.core.domain.room.ResumeOriginalViewUrl
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID

class ResumeSubmissionViewControllerTest : RestDocsTest() {
    private lateinit var resumeOriginalViewService: ResumeOriginalViewService
    private val roomId = "01920000-0000-7000-8000-000000000001"
    private val resumeSubmissionId = 4101L
    private val viewerMemberId: UUID = UUID.randomUUID()
    private val principal = Principal { viewerMemberId.toString() }

    private val summary = "이력서 원본 열람 URL 발급"
    private val description =
        "같은 룸 참여자가 참여자 명부의 resumeSubmissionId 로 제출 이력서 원본을 여는 임시 URL 을 받는다(「룸 참여」 §4.5). " +
            "URL 은 5분 뒤 만료되며, 다시 열 때는 재호출해 새 URL 을 받는다. " +
            "매 발급마다 권한을 재검증한다 - 룸에 속하지 않으면 E1419(403), 참여자여도 진행 확정 전이거나 " +
            "원본 비공개 룸이거나 룸이 끝났거나 제출자가 나가 회수된 경우는 E1429(409)로 거부된다. " +
            "본인 제출도 같은 게이트를 탄다. 제출을 찾을 수 없거나 다른 룸의 제출이면 E1010(404), " +
            "룸이 없으면 E1405(404)."

    @BeforeEach
    fun setUp() {
        resumeOriginalViewService = mockk()
        mockMvc = mockController(
            ResumeSubmissionViewController(resumeOriginalViewService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun resumeSubmissionViewUrl() {
        every { resumeOriginalViewService.issueViewUrl(any(), any(), any()) } returns ResumeOriginalViewUrl(
            url = "https://moimyeon-resume.s3.ap-northeast-2.amazonaws.com/resumes/member/resume.pdf" +
                "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=300&X-Amz-Signature=example",
            expiresAt = LocalDateTime.of(2026, 8, 13, 21, 5, 0),
        )

        mockMvc.perform(
            get("/v1/rooms/{roomId}/resume-submissions/{resumeSubmissionId}/view-url", roomId, resumeSubmissionId)
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "resumeSubmissionViewUrl",
                    summary,
                    description,
                    pathParameters(
                        parameterWithName("roomId").description("룸 id (UUID)"),
                        parameterWithName("resumeSubmissionId").description("참여자 명부가 내린 제출 이력서 id"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.url").type(JsonFieldType.STRING)
                            .description("이력서 원본을 여는 presigned URL. 만료 전까지만 유효하다"),
                        fieldWithPath("data.expiresAt").type(JsonFieldType.STRING)
                            .description("URL 만료 시각 (발급 시점 + 5분). 만료 후에는 재발급받는다"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `인증 정보 없이 호출하면 E1102`() {
        mockMvc.perform(
            get("/v1/rooms/{roomId}/resume-submissions/{resumeSubmissionId}/view-url", roomId, resumeSubmissionId),
        )
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("resumeSubmissionViewUrl-e1102", summary, description, errorResponseFields()))
    }

    @Test
    fun `룸에 속하지 않은 사용자가 호출하면 E1419`() {
        every { resumeOriginalViewService.issueViewUrl(any(), any(), any()) } throws
            CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        mockMvc.perform(
            get("/v1/rooms/{roomId}/resume-submissions/{resumeSubmissionId}/view-url", roomId, resumeSubmissionId)
                .principal(principal),
        )
            .andExpect(status().isForbidden)
            .andDo(documentApi("resumeSubmissionViewUrl-e1419", summary, description, errorResponseFields()))
    }

    @Test
    fun `참여자라도 열 수 없는 상태면 E1429`() {
        every { resumeOriginalViewService.issueViewUrl(any(), any(), any()) } throws
            CoreException(CoreErrorType.RESUME_ORIGINAL_NOT_VIEWABLE)

        mockMvc.perform(
            get("/v1/rooms/{roomId}/resume-submissions/{resumeSubmissionId}/view-url", roomId, resumeSubmissionId)
                .principal(principal),
        )
            .andExpect(status().isConflict)
            .andDo(documentApi("resumeSubmissionViewUrl-e1429", summary, description, errorResponseFields()))
    }

    @Test
    fun `제출을 찾을 수 없으면 E1010`() {
        every { resumeOriginalViewService.issueViewUrl(any(), any(), any()) } throws
            CoreException(CoreErrorType.RESUME_NOT_FOUND)

        mockMvc.perform(
            get("/v1/rooms/{roomId}/resume-submissions/{resumeSubmissionId}/view-url", roomId, resumeSubmissionId)
                .principal(principal),
        )
            .andExpect(status().isNotFound)
            .andDo(documentApi("resumeSubmissionViewUrl-e1010", summary, description, errorResponseFields()))
    }

    @Test
    fun `룸이 없으면 E1405`() {
        every { resumeOriginalViewService.issueViewUrl(any(), any(), any()) } throws
            CoreException(CoreErrorType.ROOM_NOT_FOUND)

        mockMvc.perform(
            get("/v1/rooms/{roomId}/resume-submissions/{resumeSubmissionId}/view-url", roomId, resumeSubmissionId)
                .principal(principal),
        )
            .andExpect(status().isNotFound)
            .andDo(documentApi("resumeSubmissionViewUrl-e1405", summary, description, errorResponseFields()))
    }
}
