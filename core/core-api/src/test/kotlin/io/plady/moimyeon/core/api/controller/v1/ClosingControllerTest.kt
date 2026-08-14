package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.closing.ClosingQuestion
import io.plady.moimyeon.core.domain.closing.ClosingService
import io.plady.moimyeon.core.domain.closing.ClosingSubmission
import io.plady.moimyeon.core.domain.closing.QuestionEvaluation
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.enums.QuestionVote
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID

class ClosingControllerTest : RestDocsTest() {
    private lateinit var service: ClosingService
    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000440")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val principal = Principal { memberId.toString() }

    @BeforeEach
    fun setUp() {
        service = mockk()
        mockMvc = mockController(
            ClosingController(service),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `받은 원 질문 평가와 클로징을 제출한다`() {
        val evaluations = listOf(
            QuestionEvaluation(11L, QuestionVote.MEMORABLE),
            QuestionEvaluation(12L, QuestionVote.DISAPPOINTING),
        )
        every { service.submit(memberId, roomId, evaluations) } returns ClosingSubmission(
            roomId,
            memberId,
            LocalDateTime.of(2026, 8, 14, 12, 0),
        )

        mockMvc.perform(
            post("/v1/closing-responses")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf(
                            "roomId" to roomId,
                            "evaluations" to listOf(
                                mapOf("questionId" to 11L, "vote" to "MEMORABLE"),
                                mapOf("questionId" to 12L, "vote" to "DISAPPOINTING"),
                            ),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("2026-08-14T12:00:00") }
            .andDo(
                documentApi(
                    "submitClosingResponse",
                    SUBMIT_SUMMARY,
                    SUBMIT_DESCRIPTION,
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("evaluations").description("받은 원 질문 전체의 평가"),
                        fieldWithPath("evaluations[].questionId").description("실제로 받은 원 질문 id"),
                        fieldWithPath("evaluations[].vote").description("MEMORABLE | DISAPPOINTING"),
                    ),
                    successResponseFields(
                        fieldWithPath("data.roomId").description("룸 id"),
                        fieldWithPath("data.memberId").description("제출자 회원 id"),
                        fieldWithPath("data.submittedAt").description("최초 클로징 제출 시각"),
                    ),
                ),
            )
    }

    @Test
    fun `클로징에서 평가할 실제 사용 원 질문을 조회한다`() {
        every { service.getQuestions(memberId, roomId) } returns listOf(
            ClosingQuestion(11L, memberId, "정합성을 어떻게 복구했나요?", QuestionSource.PREPARATION),
        )

        mockMvc.perform(
            get("/v1/closing-questions/me").queryParam("roomId", roomId.toString()).principal(principal),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("정합성을 어떻게 복구했나요?") }
            .andDo(
                documentApi(
                    "getMyClosingQuestions",
                    QUESTIONS_SUMMARY,
                    QUESTIONS_DESCRIPTION,
                    queryParameters(parameterWithName("roomId").description("룸 id (UUID)")),
                    successResponseFields(
                        fieldWithPath("data.questions").description("실제로 사용된 원 질문 목록"),
                        fieldWithPath("data.questions[].questionId").description("평가 대상 원 질문 id"),
                        fieldWithPath("data.questions[].authorMemberId").description("질문 작성자 회원 id"),
                        fieldWithPath("data.questions[].content").description("질문 본문"),
                        fieldWithPath("data.questions[].source").description("PREPARATION | IN_PROGRESS"),
                    ),
                ),
            )
    }

    @Test
    fun `지원하지 않는 질문 평가 값이면 E400`() {
        mockMvc.perform(
            post("/v1/closing-responses")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf(
                            "roomId" to roomId,
                            "evaluations" to listOf(mapOf("questionId" to 11L, "vote" to "NEUTRAL")),
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "submitClosingResponse-e400",
                    SUBMIT_SUMMARY,
                    SUBMIT_DESCRIPTION,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `클로징 제출 오류를 문서화한다`() {
        val evaluations = listOf(QuestionEvaluation(11L, QuestionVote.MEMORABLE))
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.CLOSING_SUBMISSION_FORBIDDEN,
            CoreErrorType.CLOSING_NOT_AVAILABLE,
            CoreErrorType.CLOSING_QUESTION_MISMATCH,
        ).forEach { errorType ->
            every { service.submit(memberId, roomId, evaluations) } throws CoreException(errorType)

            mockMvc.perform(
                post("/v1/closing-responses")
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper().writeValueAsString(
                            mapOf(
                                "roomId" to roomId,
                                "evaluations" to listOf(mapOf("questionId" to 11L, "vote" to "MEMORABLE")),
                            ),
                        ),
                    ),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "submitClosingResponse-${errorType.code.name.lowercase()}",
                        SUBMIT_SUMMARY,
                        SUBMIT_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `클로징 질문 조회 오류를 문서화한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.CLOSING_SUBMISSION_FORBIDDEN,
            CoreErrorType.CLOSING_NOT_AVAILABLE,
        ).forEach { errorType ->
            every { service.getQuestions(memberId, roomId) } throws CoreException(errorType)

            mockMvc.perform(
                get("/v1/closing-questions/me").queryParam("roomId", roomId.toString()).principal(principal),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getMyClosingQuestions-${errorType.code.name.lowercase()}",
                        QUESTIONS_SUMMARY,
                        QUESTIONS_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    private companion object {
        const val SUBMIT_SUMMARY = "클로징 제출"
        const val SUBMIT_DESCRIPTION =
            "실제로 받은 모든 원 질문을 평가하고 멱등하게 제출한다. " +
                "E400, E1405, E1801, E1802, E1803을 응답할 수 있다."
        const val QUESTIONS_SUMMARY = "내 클로징 평가 질문 조회"
        const val QUESTIONS_DESCRIPTION =
            "자기 라운드에서 실제 사용된 원 질문을 조회한다. E1405, E1801, E1802를 응답할 수 있다."
    }
}
