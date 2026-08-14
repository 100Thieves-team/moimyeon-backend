package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.FollowUpQuestionResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCardResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCardSetDetailResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCardSetSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCardSetsResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionMemberResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionResumeSummaryResponse
import io.plady.moimyeon.core.api.facade.QuestionPreparationFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.question.QuestionPreparationService
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class QuestionPreparationControllerTest : RestDocsTest() {
    private lateinit var facade: QuestionPreparationFacade
    private lateinit var questionPreparationService: QuestionPreparationService

    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000438")
    private val viewerMemberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val targetMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val authorMemberId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val principal = Principal { viewerMemberId.toString() }

    private val readDescription =
        "CONFIRMED 또는 COMPLETED 룸의 현재 참여자가 확정 시점 참여자별 질문 카드셋을 조회한다" +
            "(「룸 진행 준비」 §3, §4.1). 본인 카드셋의 내용은 반환하지 않고 준비 중인 작성자 수만 제공한다."
    private val writeDescription =
        "CONFIRMED 룸의 현재 참여자가 다른 확정 참여자에게 질문 또는 꼬리질문을 남긴다" +
            "(「룸 진행 준비」 §4.2). COMPLETED 룸은 읽기 전용이다. " +
            "본인이 대상인 원 질문에는 꼬리질문을 남길 수 없으며 E1505로 응답한다."

    @BeforeEach
    fun setUp() {
        facade = mockk()
        questionPreparationService = mockk()
        mockMvc = mockController(
            QuestionPreparationController(facade, questionPreparationService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `질문 대상 목록과 본인 카드셋 준비 인원 수를 조회한다`() {
        every { facade.getCardSets(viewerMemberId, roomId) } returns QuestionCardSetsResponse(
            myCardSetPreparerCount = 2,
            cardSets = listOf(
                QuestionCardSetSummaryResponse(
                    target = QuestionMemberResponse(targetMemberId, "성실한 사슴 03"),
                    questionCount = 3,
                    followUpQuestionCount = 5,
                ),
            ),
        )

        mockMvc.perform(
            get("/v1/rooms/{roomId}/question-sets", roomId).principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "getQuestionCardSets",
                    "진행 준비 질문 카드셋 목록 조회",
                    readDescription,
                    pathParameters(parameterWithName("roomId").description("룸 id (UUID)")),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.myCardSetPreparerCount").type(JsonFieldType.NUMBER)
                            .description("내 카드셋에 활성 질문 또는 꼬리질문을 남긴 참여자 수"),
                        fieldWithPath("data.cardSets").type(JsonFieldType.ARRAY)
                            .description("본인을 제외한 확정 시점 참여자의 카드셋 요약"),
                        fieldWithPath("data.cardSets[].target.memberId").type(JsonFieldType.STRING)
                            .description("질문 대상 회원 id (UUID)"),
                        fieldWithPath("data.cardSets[].target.nickname").type(JsonFieldType.STRING)
                            .description("질문 대상 닉네임. 탈퇴한 경우 '탈퇴한 회원'"),
                        fieldWithPath("data.cardSets[].questionCount").type(JsonFieldType.NUMBER)
                            .description("활성 원 질문 수"),
                        fieldWithPath("data.cardSets[].followUpQuestionCount").type(JsonFieldType.NUMBER)
                            .description("활성 꼬리질문 수"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `선택한 참여자의 질문과 AI 이력서 요약을 조회한다`() {
        every { facade.getCardSet(viewerMemberId, roomId, targetMemberId) } returns cardSetDetail()

        mockMvc.perform(
            get("/v1/rooms/{roomId}/question-sets/{targetMemberId}", roomId, targetMemberId)
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "getQuestionCardSet",
                    "진행 준비 질문 카드셋 상세 조회",
                    "$readDescription 대상의 제출 이력서 원본은 제공하지 않고 AI 요약 상태와 내용만 제공한다.",
                    pathParameters(
                        parameterWithName("roomId").description("룸 id (UUID)"),
                        parameterWithName("targetMemberId").description("질문 대상 회원 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.target.memberId").type(JsonFieldType.STRING).description("질문 대상 회원 id"),
                        fieldWithPath("data.target.nickname").type(JsonFieldType.STRING).description("질문 대상 닉네임"),
                        fieldWithPath("data.resumeSummary.status").type(JsonFieldType.STRING)
                            .description("AI 요약 상태 (DONE | PROCESSING | FAILED)"),
                        fieldWithPath("data.resumeSummary.text").type(JsonFieldType.STRING).optional()
                            .description("AI 요약 내용. DONE이 아니면 null"),
                        fieldWithPath("data.questions").type(JsonFieldType.ARRAY).description("원 질문 (안정적인 작성 순)"),
                        *questionFields("data.questions[]"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `다른 참여자에게 준비 질문을 남긴다`() {
        every {
            questionPreparationService.leaveQuestion(
                viewerMemberId,
                roomId,
                targetMemberId,
                "결제 연동에서 이중 결제를 어떻게 막았나요?",
            )
        } returns 11L

        mockMvc.perform(
            post("/v1/rooms/{roomId}/questions", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf(
                            "targetMemberId" to targetMemberId,
                            "content" to "결제 연동에서 이중 결제를 어떻게 막았나요?",
                        ),
                    ),
                ),
        )
            .andExpect(status().isCreated)
            .andDo(
                documentQuestionWrite(
                    identifier = "leavePreparationQuestion",
                    summary = "진행 준비 질문 작성",
                    includeTargetMemberId = true,
                ),
            )
    }

    @Test
    fun `원 질문에 꼬리질문을 남긴다`() {
        every {
            questionPreparationService.leaveFollowUp(
                viewerMemberId,
                roomId,
                11L,
                "멱등 키는 어디에 저장했나요?",
            )
        } returns 12L

        mockMvc.perform(
            post(
                "/v1/rooms/{roomId}/questions/{questionId}/follow-ups",
                roomId,
                11L,
            )
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("content" to "멱등 키는 어디에 저장했나요?"))),
        )
            .andExpect(status().isCreated)
            .andDo(
                documentQuestionWrite(
                    identifier = "leavePreparationFollowUpQuestion",
                    summary = "진행 준비 꼬리질문 작성",
                    includeTargetMemberId = false,
                ),
            )
    }

    @Test
    fun `질문 본문은 500자까지 허용한다`() {
        val content = "가".repeat(500)
        every {
            questionPreparationService.leaveQuestion(viewerMemberId, roomId, targetMemberId, content)
        } returns 13L

        mockMvc.perform(
            post("/v1/rooms/{roomId}/questions", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("targetMemberId" to targetMemberId, "content" to content))),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `질문 본문이 공백뿐이면 E400`() {
        mockMvc.perform(
            post("/v1/rooms/{roomId}/questions", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf("targetMemberId" to targetMemberId, "content" to " \t\n"),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "leavePreparationQuestion-e400-blank",
                    "진행 준비 질문 작성",
                    writeDescription,
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { questionPreparationService.leaveQuestion(any(), any(), any(), any()) }
    }

    @Test
    fun `질문 본문이 500자를 초과하면 E400`() {
        mockMvc.perform(
            post("/v1/rooms/{roomId}/questions", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf("targetMemberId" to targetMemberId, "content" to "가".repeat(501)),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "leavePreparationQuestion-e400-length",
                    "진행 준비 질문 작성",
                    writeDescription,
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { questionPreparationService.leaveQuestion(any(), any(), any(), any()) }
    }

    @Test
    fun `질문 본문의 특수문자와 이모지는 그대로 허용한다`() {
        val content = "C++에서 <T>와 @Qualifier를 왜 썼나요? 🤔"
        every {
            questionPreparationService.leaveQuestion(viewerMemberId, roomId, targetMemberId, content)
        } returns 14L

        mockMvc.perform(
            post("/v1/rooms/{roomId}/questions", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("targetMemberId" to targetMemberId, "content" to content))),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `본인이 작성한 질문을 삭제한다`() {
        justRun { questionPreparationService.deleteQuestion(viewerMemberId, roomId, 11L) }

        mockMvc.perform(
            delete("/v1/rooms/{roomId}/questions/{questionId}", roomId, 11L).principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "deletePreparationQuestion",
                    "진행 준비 질문 삭제",
                    "CONFIRMED 룸에서 본인이 작성한 질문 또는 꼬리질문을 소프트 삭제한다. " +
                        "다른 작성자의 활성 꼬리질문이 달린 원 질문은 E1508로 거부한다.",
                    pathParameters(
                        parameterWithName("roomId").description("룸 id (UUID)"),
                        parameterWithName("questionId").description("삭제할 질문 또는 꼬리질문 id"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `인증 없이 카드셋을 조회하면 E1102`() {
        mockMvc.perform(get("/v1/rooms/{roomId}/question-sets", roomId))
            .andExpect(status().isUnauthorized)
            .andDo(
                documentApi(
                    "getQuestionCardSets-e1102",
                    "진행 준비 질문 카드셋 목록 조회",
                    readDescription,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `현재 참여자가 아니면 카드셋 목록 조회는 E1502`() {
        every {
            facade.getCardSets(viewerMemberId, roomId)
        } throws CoreException(CoreErrorType.QUESTION_CARD_SET_FORBIDDEN)

        mockMvc.perform(
            get("/v1/rooms/{roomId}/question-sets", roomId).principal(principal),
        )
            .andExpect(status().isForbidden)
            .andDo(
                documentApi(
                    "getQuestionCardSets-e1502",
                    "진행 준비 질문 카드셋 목록 조회",
                    readDescription,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `확정 상태가 아니면 질문 작성은 E1504`() {
        every {
            questionPreparationService.leaveQuestion(viewerMemberId, roomId, targetMemberId, "질문")
        } throws CoreException(CoreErrorType.QUESTION_PREPARATION_NOT_OPEN)

        mockMvc.perform(
            post("/v1/rooms/{roomId}/questions", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf("targetMemberId" to targetMemberId, "content" to "질문"),
                    ),
                ),
        )
            .andExpect(status().isConflict)
            .andDo(
                documentApi(
                    "leavePreparationQuestion-e1504",
                    "진행 준비 질문 작성",
                    writeDescription,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `본인이 대상인 원 질문에 꼬리질문을 남기면 E1505`() {
        every {
            questionPreparationService.leaveFollowUp(viewerMemberId, roomId, 11L, "꼬리질문")
        } throws CoreException(CoreErrorType.QUESTION_PREPARATION_FORBIDDEN)

        mockMvc.perform(
            post("/v1/rooms/{roomId}/questions/{questionId}/follow-ups", roomId, 11L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("content" to "꼬리질문"))),
        )
            .andExpect(status().isForbidden)
            .andDo(
                documentApi(
                    "leavePreparationFollowUpQuestion-e1505",
                    "진행 준비 꼬리질문 작성",
                    writeDescription,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `다른 작성자의 꼬리질문이 달린 원 질문 삭제는 E1508`() {
        every {
            questionPreparationService.deleteQuestion(viewerMemberId, roomId, 11L)
        } throws CoreException(CoreErrorType.QUESTION_HAS_OTHER_FOLLOW_UP)

        mockMvc.perform(
            delete("/v1/rooms/{roomId}/questions/{questionId}", roomId, 11L).principal(principal),
        )
            .andExpect(status().isConflict)
            .andDo(
                documentApi(
                    "deletePreparationQuestion-e1508",
                    "진행 준비 질문 삭제",
                    "다른 작성자의 활성 꼬리질문이 달린 원 질문은 삭제하지 않는다.",
                    errorResponseFields(),
                ),
            )
    }

    private fun cardSetDetail(): QuestionCardSetDetailResponse {
        val author = QuestionMemberResponse(authorMemberId, "든든한 곰 21")
        return QuestionCardSetDetailResponse(
            target = QuestionMemberResponse(targetMemberId, "성실한 사슴 03"),
            resumeSummary = QuestionResumeSummaryResponse("DONE", "결제 연동과 배치 처리 경험이 있어요."),
            questions = listOf(
                QuestionCardResponse(
                    questionId = 11L,
                    author = author,
                    content = "결제 연동에서 이중 결제를 어떻게 막았나요?",
                    source = "PREPARATION",
                    asked = false,
                    followUps = listOf(
                        FollowUpQuestionResponse(
                            questionId = 12L,
                            author = author,
                            content = "멱등 키는 어디에 저장했나요?",
                            source = "PREPARATION",
                            asked = false,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun questionFields(prefix: String) = arrayOf(
        fieldWithPath("$prefix.questionId").type(JsonFieldType.NUMBER).description("원 질문 id"),
        fieldWithPath("$prefix.author.memberId").type(JsonFieldType.STRING).description("작성자 회원 id"),
        fieldWithPath("$prefix.author.nickname").type(JsonFieldType.STRING).description("작성자 닉네임"),
        fieldWithPath("$prefix.content").type(JsonFieldType.STRING).description("질문 본문"),
        fieldWithPath("$prefix.source").type(JsonFieldType.STRING).description("작성 단계 (PREPARATION | IN_PROGRESS)"),
        fieldWithPath("$prefix.asked").type(JsonFieldType.BOOLEAN).description("진행 중 사용 여부"),
        fieldWithPath("$prefix.followUps").type(JsonFieldType.ARRAY).description("꼬리질문 (작성 순)"),
        fieldWithPath("$prefix.followUps[].questionId").type(JsonFieldType.NUMBER).description("꼬리질문 id"),
        fieldWithPath("$prefix.followUps[].author.memberId").type(JsonFieldType.STRING).description("꼬리질문 작성자 회원 id"),
        fieldWithPath("$prefix.followUps[].author.nickname").type(JsonFieldType.STRING).description("꼬리질문 작성자 닉네임"),
        fieldWithPath("$prefix.followUps[].content").type(JsonFieldType.STRING).description("꼬리질문 본문"),
        fieldWithPath("$prefix.followUps[].source").type(JsonFieldType.STRING)
            .description("작성 단계 (PREPARATION | IN_PROGRESS)"),
        fieldWithPath("$prefix.followUps[].asked").type(JsonFieldType.BOOLEAN).description("진행 중 사용 여부"),
    )

    private fun documentQuestionWrite(
        identifier: String,
        summary: String,
        includeTargetMemberId: Boolean,
    ) = documentApi(
        identifier,
        summary,
        writeDescription,
        pathParameters(
            parameterWithName("roomId").description("룸 id (UUID)"),
            *if (!includeTargetMemberId) {
                arrayOf(parameterWithName("questionId").description("원 질문 id"))
            } else {
                emptyArray()
            },
        ),
        requestFields(
            *if (includeTargetMemberId) {
                arrayOf(
                    fieldWithPath("targetMemberId").type(JsonFieldType.STRING)
                        .description("질문 대상 회원 id (UUID)"),
                )
            } else {
                emptyArray()
            },
            fieldWithPath("content").type(JsonFieldType.STRING).description("질문 본문 (공백 불가, 최대 500자)"),
        ),
        responseFields(
            fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
            fieldWithPath("data.questionId").type(JsonFieldType.NUMBER).description("생성된 질문 id"),
            fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
        ),
    )
}
