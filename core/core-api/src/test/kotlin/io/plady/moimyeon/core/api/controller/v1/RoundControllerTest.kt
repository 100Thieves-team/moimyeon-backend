package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.FollowUpQuestionResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCardResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionMemberResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoundScreenResponse
import io.plady.moimyeon.core.api.facade.RoundFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.question.QuestionProgressService
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class RoundControllerTest : RestDocsTest() {
    private lateinit var roundFacade: RoundFacade
    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000440")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val intervieweeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val principal = Principal { memberId.toString() }

    @BeforeEach
    fun setUp() {
        roundFacade = mockk()
        mockMvc = mockController(
            RoundController(roundFacade),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `면접 참여자는 질문 카드가 있는 역할 화면을 조회한다`() {
        every { roundFacade.getScreen(memberId, roomId, intervieweeId) } returns RoundScreenResponse(
            role = "PARTICIPANT",
            interviewee = QuestionMemberResponse(intervieweeId, "성실한 사슴 03"),
            questions = listOf(
                QuestionCardResponse(
                    questionId = 11L,
                    author = QuestionMemberResponse(memberId, "꼼꼼한 여우 12"),
                    content = "정합성을 어떻게 복구했나요?",
                    source = "PREPARATION",
                    asked = true,
                    followUps = listOf(
                        FollowUpQuestionResponse(
                            questionId = 12L,
                            author = QuestionMemberResponse(memberId, "꼼꼼한 여우 12"),
                            content = "복구 순서는 어떻게 정했나요?",
                            source = "IN_PROGRESS",
                            asked = false,
                        ),
                    ),
                ),
            ),
        )

        mockMvc.perform(
            get("/v1/rounds")
                .queryParam("roomId", roomId.toString())
                .queryParam("intervieweeMemberId", intervieweeId.toString())
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"role\":\"PARTICIPANT\"") }
            .andExpect { assertThat(it.response.contentAsString).contains("정합성을 어떻게 복구했나요?") }
            .andDo(
                documentApi(
                    "getRoundScreen",
                    ROUND_SUMMARY,
                    ROUND_DESCRIPTION,
                    queryParameters(
                        parameterWithName("roomId").description("룸 id (UUID)"),
                        parameterWithName("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                    ),
                    successResponseFields(
                        fieldWithPath("data.role").description("INTERVIEWEE | PARTICIPANT"),
                        fieldWithPath("data.interviewee.memberId").description("현재 라운드 면접자 회원 id"),
                        fieldWithPath("data.interviewee.nickname").description("현재 라운드 면접자 표시 이름"),
                        fieldWithPath("data.questions").description("참여자용 질문 카드, 면접자는 null"),
                        fieldWithPath("data.questions[].questionId").description("원 질문 id"),
                        fieldWithPath("data.questions[].author.memberId").description("원 질문 작성자 회원 id"),
                        fieldWithPath("data.questions[].author.nickname").description("원 질문 작성자 표시 이름"),
                        fieldWithPath("data.questions[].content").description("원 질문 본문"),
                        fieldWithPath("data.questions[].source").description("PREPARATION | IN_PROGRESS"),
                        fieldWithPath("data.questions[].asked").description("질문 사용 여부"),
                        fieldWithPath("data.questions[].followUps").description("꼬리질문 목록"),
                        fieldWithPath("data.questions[].followUps[].questionId").description("꼬리질문 id"),
                        fieldWithPath("data.questions[].followUps[].author.memberId")
                            .description("꼬리질문 작성자 회원 id"),
                        fieldWithPath("data.questions[].followUps[].author.nickname")
                            .description("꼬리질문 작성자 표시 이름"),
                        fieldWithPath("data.questions[].followUps[].content").description("꼬리질문 본문"),
                        fieldWithPath("data.questions[].followUps[].source")
                            .description("PREPARATION | IN_PROGRESS"),
                        fieldWithPath("data.questions[].followUps[].asked").description("꼬리질문 사용 여부"),
                    ),
                ),
            )
    }

    @Test
    fun `라운드 화면 조회 오류를 문서화한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.ROOM_PROGRESS_FORBIDDEN,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
            CoreErrorType.QUESTION_CARD_SET_FORBIDDEN,
            CoreErrorType.QUESTION_CARD_SET_NOT_FOUND,
        ).forEach { errorType ->
            every { roundFacade.getScreen(memberId, roomId, intervieweeId) } throws CoreException(errorType)

            mockMvc.perform(
                get("/v1/rounds")
                    .queryParam("roomId", roomId.toString())
                    .queryParam("intervieweeMemberId", intervieweeId.toString())
                    .principal(principal),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getRoundScreen-${errorType.code.name.lowercase()}",
                        ROUND_SUMMARY,
                        ROUND_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    private companion object {
        const val ROUND_SUMMARY = "라운드 화면 조회"
        const val ROUND_DESCRIPTION =
            "면접자에게는 질문을 숨기고, 나머지 확정 참여자에게는 질문 카드셋을 제공한다. " +
                "E1405, E1502, E1503, E1703, E1704를 응답할 수 있다."
    }
}

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
