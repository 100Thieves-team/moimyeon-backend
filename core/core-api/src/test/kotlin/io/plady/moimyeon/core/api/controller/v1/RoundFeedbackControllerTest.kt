package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.roundfeedback.FinalFeedbackCard
import io.plady.moimyeon.core.domain.roundfeedback.IntervieweeRoundFeedback
import io.plady.moimyeon.core.domain.roundfeedback.RoundFeedbackAuthor
import io.plady.moimyeon.core.domain.roundfeedback.RoundFeedbackAuthorRole
import io.plady.moimyeon.core.domain.roundfeedback.RoundFeedbackService
import io.plady.moimyeon.core.domain.roundfeedback.RoundQuestionComment
import io.plady.moimyeon.core.domain.roundfeedback.RoundQuestionRecord
import io.plady.moimyeon.core.domain.roundfeedback.SelfFeedback
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID

class RoundFeedbackControllerTest : RestDocsTest() {
    private lateinit var service: RoundFeedbackService
    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000440")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val intervieweeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val principal = Principal { memberId.toString() }
    private val feedbackPath = "/v1/round-feedbacks"

    @BeforeEach
    fun setUp() {
        service = mockk()
        mockMvc = mockController(
            RoundFeedbackController(service),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `내 질문 메모를 질문별로 조회한다`() {
        every { service.getMyQuestionRecords(memberId, roomId, intervieweeId) } returns listOf(
            RoundQuestionRecord(
                11L,
                "정합성을 어떻게 복구했나요?",
                listOf(RoundQuestionComment(21L, QuestionCommentType.MEMO, "기록", LocalDateTime.of(2026, 8, 14, 10, 0))),
            ),
        )

        mockMvc.perform(
            get("/v1/question-records/me")
                .queryParam("roomId", roomId.toString())
                .queryParam("intervieweeMemberId", intervieweeId.toString())
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("정합성을 어떻게 복구했나요?") }
            .andDo(
                documentApi(
                    "getMyRoundQuestionRecords",
                    RECORDS_SUMMARY,
                    RECORDS_DESCRIPTION,
                    feedbackQueryParameters(),
                    successResponseFields(
                        fieldWithPath("data.records").description("질문별 내 기록 목록"),
                        fieldWithPath("data.records[].questionId").description("실제로 질문한 원 질문 id"),
                        fieldWithPath("data.records[].questionContent").description("원 질문 본문"),
                        fieldWithPath("data.records[].comments").description("해당 질문에 작성한 내 메모"),
                        fieldWithPath("data.records[].comments[].commentId").description("질문 메모 id"),
                        fieldWithPath("data.records[].comments[].type")
                            .description("MEMO | GOOD_POINT | IMPROVEMENT_POINT"),
                        fieldWithPath("data.records[].comments[].content").description("질문 메모 본문"),
                        fieldWithPath("data.records[].comments[].createdAt").description("질문 메모 작성 시각"),
                    ),
                ),
            )
    }

    @Test
    fun `라운드 최종 피드백을 한 건 제출한다`() {
        every { service.leaveFinalFeedback(memberId, roomId, intervieweeId, "최종 피드백") } returns 31L

        mockMvc.perform(
            post("/v1/final-feedbacks")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(feedbackRequest("최종 피드백")),
        )
            .andExpect(status().isCreated)
            .andExpect { assertThat(it.response.contentAsString).contains("\"feedbackId\":31") }
            .andDo(
                documentApi(
                    "leaveFinalRoundFeedback",
                    FINAL_SUMMARY,
                    FINAL_DESCRIPTION,
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                        fieldWithPath("content").description("공백이 아닌 최종 피드백 본문"),
                    ),
                    successResponseFields(fieldWithPath("data.feedbackId").description("생성된 최종 피드백 id")),
                ),
            )
    }

    @Test
    fun `면접자는 자가 피드백을 저장하거나 수정한다`() {
        every { service.leaveSelfFeedback(memberId, roomId, intervieweeId, "자가 피드백") } returns 32L

        mockMvc.perform(
            put("/v1/self-feedbacks")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(feedbackRequest("자가 피드백")),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"feedbackId\":32") }
            .andDo(
                documentApi(
                    "saveSelfRoundFeedback",
                    SELF_SUMMARY,
                    SELF_DESCRIPTION,
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                        fieldWithPath("content").description("공백이 아닌 자가 피드백 본문"),
                    ),
                    successResponseFields(fieldWithPath("data.feedbackId").description("저장된 자가 피드백 id")),
                ),
            )
    }

    @Test
    fun `면접자는 잠긴 최종 피드백 카드와 자가 피드백을 조회한다`() {
        every { service.getIntervieweeFeedback(memberId, roomId, intervieweeId) } returns IntervieweeRoundFeedback(
            selfFeedback = SelfFeedback(32L, "자가 피드백"),
            finalFeedbacks = listOf(
                FinalFeedbackCard(
                    31L,
                    RoundFeedbackAuthor(memberId, "꼼꼼한 여우 12", RoundFeedbackAuthorRole.PARTICIPANT),
                    null,
                    false,
                ),
                FinalFeedbackCard(
                    33L,
                    RoundFeedbackAuthor(memberId, "꼼꼼한 여우 12", RoundFeedbackAuthorRole.PARTICIPANT),
                    "열람한 최종 피드백",
                    true,
                ),
            ),
        )

        mockMvc.perform(
            get(feedbackPath)
                .queryParam("roomId", roomId.toString())
                .queryParam("intervieweeMemberId", intervieweeId.toString())
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"revealed\":false") }
            .andExpect { assertThat(it.response.contentAsString).contains("\"content\":null") }
            .andDo(
                documentApi(
                    "getIntervieweeRoundFeedback",
                    GET_SUMMARY,
                    GET_DESCRIPTION,
                    feedbackQueryParameters(),
                    successResponseFields(
                        fieldWithPath("data.selfFeedback").optional()
                            .description("자가 피드백, 작성하지 않았으면 null"),
                        fieldWithPath("data.selfFeedback.feedbackId").description("자가 피드백 id"),
                        fieldWithPath("data.selfFeedback.content").description("자가 피드백 본문"),
                        fieldWithPath("data.finalFeedbacks").description("최종 피드백 카드 목록"),
                        fieldWithPath("data.finalFeedbacks[].feedbackId").description("최종 피드백 id"),
                        fieldWithPath("data.finalFeedbacks[].author.memberId").description("작성자 회원 id"),
                        fieldWithPath("data.finalFeedbacks[].author.displayName").description("작성자 표시 이름"),
                        fieldWithPath("data.finalFeedbacks[].author.role").description("작성자 역할 (PARTICIPANT)"),
                        fieldWithPath("data.finalFeedbacks[].content").optional()
                            .description("열람 확인 후 피드백 본문, 확인 전 null"),
                        fieldWithPath("data.finalFeedbacks[].revealed").description("카드 열람 확인 여부"),
                    ),
                ),
            )
    }

    @Test
    fun `면접자는 선택한 피드백 카드만 열람 확인한다`() {
        justRun { service.confirmFinalFeedbackDisclosure(memberId, roomId, intervieweeId, 31L) }

        mockMvc.perform(
            put("/v1/feedback-disclosures/{feedbackId}", 31L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(disclosureRequest()),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "confirmRoundFeedbackDisclosure",
                    DISCLOSURE_SUMMARY,
                    DISCLOSURE_DESCRIPTION,
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                    ),
                    pathParameters(parameterWithName("feedbackId").description("최종 피드백 id")),
                    emptySuccessResponseFields(),
                ),
            )
    }

    @Test
    fun `최종 피드백 본문이 공백이면 E400`() {
        mockMvc.perform(
            post("/v1/final-feedbacks")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(feedbackRequest("\n\t")),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "leaveFinalRoundFeedback-e400",
                    FINAL_SUMMARY,
                    FINAL_DESCRIPTION,
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { service.leaveFinalFeedback(any(), any(), any(), any()) }
    }

    @Test
    fun `자가 피드백 본문이 공백이면 E400`() {
        mockMvc.perform(
            put("/v1/self-feedbacks")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(feedbackRequest("  ")),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "saveSelfRoundFeedback-e400",
                    SELF_SUMMARY,
                    SELF_DESCRIPTION,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `내 질문 기록 조회 오류를 문서화한다`() {
        writeAccessErrors().forEach { errorType ->
            every { service.getMyQuestionRecords(memberId, roomId, intervieweeId) } throws CoreException(errorType)

            mockMvc.perform(
                get("/v1/question-records/me")
                    .queryParam("roomId", roomId.toString())
                    .queryParam("intervieweeMemberId", intervieweeId.toString())
                    .principal(principal),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getMyRoundQuestionRecords-${errorType.code.name.lowercase()}",
                        RECORDS_SUMMARY,
                        RECORDS_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `최종 피드백 작성 오류를 문서화한다`() {
        (writeAccessErrors() + CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS).forEach { errorType ->
            every { service.leaveFinalFeedback(memberId, roomId, intervieweeId, "최종 피드백") } throws
                CoreException(errorType)

            mockMvc.perform(
                post("/v1/final-feedbacks")
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(feedbackRequest("최종 피드백")),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "leaveFinalRoundFeedback-${errorType.code.name.lowercase()}",
                        FINAL_SUMMARY,
                        FINAL_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `자가 피드백 저장 오류를 문서화한다`() {
        writeAccessErrors().forEach { errorType ->
            every { service.leaveSelfFeedback(memberId, roomId, intervieweeId, "자가 피드백") } throws
                CoreException(errorType)

            mockMvc.perform(
                put("/v1/self-feedbacks")
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(feedbackRequest("자가 피드백")),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "saveSelfRoundFeedback-${errorType.code.name.lowercase()}",
                        SELF_SUMMARY,
                        SELF_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `면접자 피드백 조회 오류를 문서화한다`() {
        viewErrors().forEach { errorType ->
            every { service.getIntervieweeFeedback(memberId, roomId, intervieweeId) } throws CoreException(errorType)

            mockMvc.perform(
                get(feedbackPath)
                    .queryParam("roomId", roomId.toString())
                    .queryParam("intervieweeMemberId", intervieweeId.toString())
                    .principal(principal),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getIntervieweeRoundFeedback-${errorType.code.name.lowercase()}",
                        GET_SUMMARY,
                        GET_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `최종 피드백 열람 오류를 문서화한다`() {
        (viewErrors() + CoreErrorType.ROUND_FEEDBACK_NOT_FOUND).forEach { errorType ->
            every { service.confirmFinalFeedbackDisclosure(memberId, roomId, intervieweeId, 31L) } throws
                CoreException(errorType)

            mockMvc.perform(
                put("/v1/feedback-disclosures/{feedbackId}", 31L)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(disclosureRequest()),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "confirmRoundFeedbackDisclosure-${errorType.code.name.lowercase()}",
                        DISCLOSURE_SUMMARY,
                        DISCLOSURE_DESCRIPTION,
                        pathParameters(parameterWithName("feedbackId").description("최종 피드백 id")),
                        errorResponseFields(),
                    ),
                )
        }
    }

    private fun writeAccessErrors(): List<CoreErrorType> = listOf(
        CoreErrorType.ROOM_NOT_FOUND,
        CoreErrorType.ROUND_FEEDBACK_FORBIDDEN,
        CoreErrorType.ROUND_FEEDBACK_NOT_EDITABLE,
    )

    private fun viewErrors(): List<CoreErrorType> = listOf(
        CoreErrorType.ROOM_NOT_FOUND,
        CoreErrorType.ROUND_FEEDBACK_FORBIDDEN,
        CoreErrorType.ROUND_FEEDBACK_NOT_VIEWABLE,
    )

    private fun feedbackQueryParameters() = queryParameters(
        parameterWithName("roomId").description("룸 id (UUID)"),
        parameterWithName("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
    )

    private fun feedbackRequest(content: String): String = jsonMapper().writeValueAsString(
        mapOf("roomId" to roomId, "intervieweeMemberId" to intervieweeId, "content" to content),
    )

    private fun disclosureRequest(): String = jsonMapper().writeValueAsString(
        mapOf("roomId" to roomId, "intervieweeMemberId" to intervieweeId),
    )

    private companion object {
        const val RECORDS_SUMMARY = "내 라운드 질문 기록 조회"
        const val RECORDS_DESCRIPTION =
            "질문한 원 질문과 내 메모를 조회한다. E1405, E1902, E1903을 응답할 수 있다."
        const val FINAL_SUMMARY = "라운드 최종 피드백 작성"
        const val FINAL_DESCRIPTION =
            "참여자가 최종 피드백을 한 건 제출한다. E400, E1405, E1901, E1902, E1903을 응답할 수 있다."
        const val SELF_SUMMARY = "면접자 자가 피드백 저장"
        const val SELF_DESCRIPTION =
            "면접자가 자가 피드백을 저장하거나 수정한다. E400, E1405, E1902, E1903을 응답할 수 있다."
        const val GET_SUMMARY = "면접자 라운드 피드백 조회"
        const val GET_DESCRIPTION =
            "면접자가 피드백 카드 상태를 조회한다. E1405, E1902, E1904를 응답할 수 있다."
        const val DISCLOSURE_SUMMARY = "최종 피드백 카드 열람 확인"
        const val DISCLOSURE_DESCRIPTION =
            "면접자가 카드 한 건을 열람 확인한다. E1405, E1902, E1904, E1905를 응답할 수 있다."
    }
}
