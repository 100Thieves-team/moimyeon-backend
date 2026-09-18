package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.question.QuestionProgressService
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class QuestionProgressControllerTest : RestDocsTest() {
    private lateinit var service: QuestionProgressService
    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000440")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val intervieweeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val principal = Principal { memberId.toString() }

    @BeforeEach
    fun setUp() {
        service = mockk()
        mockMvc = mockController(
            QuestionProgressController(service),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `진행 중 즉석 질문을 추가한다`() {
        every { service.leaveQuestion(memberId, roomId, intervieweeId, "즉석 질문") } returns 11L

        mockMvc.perform(
            post("/v1/questions")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(progressQuestionRequest("즉석 질문")),
        )
            .andExpect(status().isCreated)
            .andExpect { assertThat(it.response.contentAsString).contains("\"questionId\":11") }
            .andDo(
                documentApi(
                    "leaveProgressQuestion",
                    QUESTION_SUMMARY,
                    QUESTION_DESCRIPTION,
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                        fieldWithPath("content").description("질문 본문 (1~500자)"),
                    ),
                    successResponseFields(fieldWithPath("data.questionId").description("생성된 원 질문 id")),
                ),
            )
    }

    @Test
    fun `진행 중 꼬리질문을 추가한다`() {
        every { service.leaveFollowUp(memberId, roomId, intervieweeId, 11L, "꼬리질문") } returns 12L

        mockMvc.perform(
            post("/v1/follow-up-questions")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(followUpQuestionRequest("꼬리질문")),
        )
            .andExpect(status().isCreated)
            .andExpect { assertThat(it.response.contentAsString).contains("\"questionId\":12") }
            .andDo(
                documentApi(
                    "leaveProgressFollowUpQuestion",
                    FOLLOW_UP_SUMMARY,
                    FOLLOW_UP_DESCRIPTION,
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                        fieldWithPath("questionId").description("부모 원 질문 id"),
                        fieldWithPath("content").description("꼬리질문 본문 (1~500자)"),
                    ),
                    successResponseFields(fieldWithPath("data.questionId").description("생성된 꼬리질문 id")),
                ),
            )
    }

    @Test
    fun `질문했어요 표시를 체크하거나 해제한다`() {
        justRun { service.changeAsked(memberId, roomId, intervieweeId, 11L, false) }

        mockMvc.perform(
            patch("/v1/questions/{questionId}", 11L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeAskedRequest(false)),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "changeQuestionAsked",
                    ASKED_SUMMARY,
                    ASKED_DESCRIPTION,
                    pathParameters(
                        parameterWithName("questionId").description("질문 또는 꼬리질문 id"),
                    ),
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                        fieldWithPath("asked").description("질문했으면 true, 되돌리면 false"),
                    ),
                    emptySuccessResponseFields(),
                ),
            )
    }

    @Test
    fun `즉석 질문 본문이 공백이면 E400`() {
        mockMvc.perform(
            post("/v1/questions")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(progressQuestionRequest("  ")),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "leaveProgressQuestion-e400",
                    QUESTION_SUMMARY,
                    QUESTION_DESCRIPTION,
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { service.leaveQuestion(any(), any(), any(), any()) }
    }

    @Test
    fun `진행 중 꼬리질문 본문이 공백이면 E400`() {
        mockMvc.perform(
            post("/v1/follow-up-questions")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(followUpQuestionRequest("  ")),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "leaveProgressFollowUpQuestion-e400",
                    FOLLOW_UP_SUMMARY,
                    FOLLOW_UP_DESCRIPTION,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `질문 사용 여부가 누락되면 E400`() {
        mockMvc.perform(
            patch("/v1/questions/{questionId}", 11L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf("roomId" to roomId, "intervieweeMemberId" to intervieweeId),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "changeQuestionAsked-e400",
                    ASKED_SUMMARY,
                    ASKED_DESCRIPTION,
                    questionIdPathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `진행 질문 도메인 오류를 문서화한다`() {
        progressErrors().forEach { errorType ->
            every { service.leaveQuestion(memberId, roomId, intervieweeId, "즉석 질문") } throws
                CoreException(errorType)

            mockMvc.perform(
                post("/v1/questions")
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(progressQuestionRequest("즉석 질문")),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "leaveProgressQuestion-${errorType.code.name.lowercase()}",
                        QUESTION_SUMMARY,
                        QUESTION_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `진행 꼬리질문 도메인 오류를 문서화한다`() {
        (progressErrors() + CoreErrorType.QUESTION_NOT_FOUND).forEach { errorType ->
            every { service.leaveFollowUp(memberId, roomId, intervieweeId, 11L, "꼬리질문") } throws
                CoreException(errorType)

            mockMvc.perform(
                post("/v1/follow-up-questions")
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(followUpQuestionRequest("꼬리질문")),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "leaveProgressFollowUpQuestion-${errorType.code.name.lowercase()}",
                        FOLLOW_UP_SUMMARY,
                        FOLLOW_UP_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `질문 사용 여부 변경 오류를 문서화한다`() {
        (progressErrors() + CoreErrorType.QUESTION_NOT_FOUND).forEach { errorType ->
            every { service.changeAsked(memberId, roomId, intervieweeId, 11L, false) } throws
                CoreException(errorType)

            mockMvc.perform(
                patch("/v1/questions/{questionId}", 11L)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(changeAskedRequest(false)),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "changeQuestionAsked-${errorType.code.name.lowercase()}",
                        ASKED_SUMMARY,
                        ASKED_DESCRIPTION,
                        questionIdPathParameters(),
                        errorResponseFields(),
                    ),
                )
        }
    }

    private fun progressErrors(): List<CoreErrorType> = listOf(
        CoreErrorType.ROOM_NOT_FOUND,
        CoreErrorType.ROOM_PROGRESS_FORBIDDEN,
        CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
        CoreErrorType.QUESTION_CARD_SET_FORBIDDEN,
        CoreErrorType.QUESTION_CARD_SET_NOT_FOUND,
    )

    private fun progressQuestionRequest(content: String): String = jsonMapper().writeValueAsString(
        mapOf("roomId" to roomId, "intervieweeMemberId" to intervieweeId, "content" to content),
    )

    private fun followUpQuestionRequest(content: String): String = jsonMapper().writeValueAsString(
        mapOf(
            "roomId" to roomId,
            "intervieweeMemberId" to intervieweeId,
            "questionId" to 11L,
            "content" to content,
        ),
    )

    private fun changeAskedRequest(asked: Boolean): String = jsonMapper().writeValueAsString(
        mapOf("roomId" to roomId, "intervieweeMemberId" to intervieweeId, "asked" to asked),
    )

    private fun questionIdPathParameters() = pathParameters(
        parameterWithName("questionId").description("질문 또는 꼬리질문 id"),
    )

    private companion object {
        const val QUESTION_SUMMARY = "진행 중 즉석 질문 추가"
        const val QUESTION_DESCRIPTION =
            "면접자 외 확정 참여자가 현재 라운드에 즉석 원 질문을 추가한다. " +
                "E400, E1405, E1502, E1503, E1703, E1704를 응답할 수 있다."
        const val FOLLOW_UP_SUMMARY = "진행 중 꼬리질문 추가"
        const val FOLLOW_UP_DESCRIPTION =
            "면접자 외 확정 참여자가 현재 라운드 원 질문에 꼬리질문을 추가한다. " +
                "E400, E1405, E1502, E1503, E1507, E1703, E1704를 응답할 수 있다."
        const val ASKED_SUMMARY = "질문 사용 여부 변경"
        const val ASKED_DESCRIPTION =
            "진행 중 질문했어요 표시를 체크하거나 해제한다. " +
                "E400, E1405, E1502, E1503, E1507, E1703, E1704를 응답할 수 있다."
    }
}
