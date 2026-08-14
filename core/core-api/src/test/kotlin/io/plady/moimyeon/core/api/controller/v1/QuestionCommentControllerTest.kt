package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCommentAuthorResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCommentCursorResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCommentResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCommentsResponse
import io.plady.moimyeon.core.api.facade.QuestionCommentFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.question.QuestionCommentCursor
import io.plady.moimyeon.core.domain.question.QuestionCommentService
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
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
import java.time.LocalDateTime
import java.util.UUID

class QuestionCommentControllerTest : RestDocsTest() {
    private lateinit var service: QuestionCommentService
    private lateinit var facade: QuestionCommentFacade
    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000440")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val intervieweeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val principal = Principal { memberId.toString() }
    private val basePath = "/v1/question-comments"

    @BeforeEach
    fun setUp() {
        service = mockk()
        facade = mockk()
        mockMvc = mockController(
            QuestionCommentController(service, facade),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `선택한 원 질문의 메모를 오래된 순으로 조회한다`() {
        val cursor = QuestionCommentCursor(LocalDateTime.of(2026, 8, 14, 9, 59), 20L)
        every { facade.getComments(memberId, roomId, intervieweeId, 11L, cursor) } returns
            QuestionCommentsResponse(
                comments = listOf(
                    QuestionCommentResponse(
                        commentId = 21L,
                        author = QuestionCommentAuthorResponse(memberId, "꼼꼼한 여우 12", true),
                        type = "GOOD_POINT",
                        content = "장애 시나리오부터 짚은 점이 좋았어요",
                        createdAt = LocalDateTime.of(2026, 8, 14, 10, 0),
                    ),
                ),
                nextCursor = QuestionCommentCursorResponse(LocalDateTime.of(2026, 8, 14, 10, 0), 21L),
            )

        mockMvc.perform(
            get(basePath)
                .queryParam("roomId", roomId.toString())
                .queryParam("intervieweeMemberId", intervieweeId.toString())
                .queryParam("questionId", "11")
                .queryParam("cursorCreatedAt", "2026-08-14T09:59:00")
                .queryParam("cursorId", "20")
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"commentId\":21") }
            .andExpect { assertThat(it.response.contentAsString).contains("\"mine\":true") }
            .andDo(
                documentApi(
                    "getQuestionComments",
                    GET_SUMMARY,
                    GET_DESCRIPTION,
                    queryParameters(
                        parameterWithName("roomId").description("룸 id (UUID)"),
                        parameterWithName("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                        parameterWithName("questionId").description("원 질문 id"),
                        parameterWithName("cursorCreatedAt").optional().description("이전 페이지 마지막 생성 시각"),
                        parameterWithName("cursorId").optional().description("이전 페이지 마지막 질문 메모 id"),
                    ),
                    successResponseFields(
                        fieldWithPath("data.comments").description("오래된 순 질문 메모 목록"),
                        fieldWithPath("data.comments[].commentId").description("질문 메모 id"),
                        fieldWithPath("data.comments[].author.memberId").description("작성자 회원 id"),
                        fieldWithPath("data.comments[].author.nickname").description("작성자 표시 이름"),
                        fieldWithPath("data.comments[].author.mine").description("로그인 회원이 작성했는지 여부"),
                        fieldWithPath("data.comments[].type").description("MEMO | GOOD_POINT | IMPROVEMENT_POINT"),
                        fieldWithPath("data.comments[].content").description("질문 메모 본문"),
                        fieldWithPath("data.comments[].createdAt").description("질문 메모 작성 시각"),
                        fieldWithPath("data.nextCursor").optional().description("다음 페이지 커서, 없으면 null"),
                        fieldWithPath("data.nextCursor.createdAt").optional().description("다음 커서 생성 시각"),
                        fieldWithPath("data.nextCursor.id").optional().description("다음 커서 질문 메모 id"),
                    ),
                ),
            )
    }

    @Test
    fun `질문 메모를 최초 MEMO 유형으로 추가한다`() {
        every {
            service.leaveComment(memberId, roomId, intervieweeId, 11L, QuestionCommentType.MEMO, "기록")
        } returns 21L

        mockMvc.perform(
            post(basePath)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createCommentRequest("MEMO", "기록")),
        )
            .andExpect(status().isCreated)
            .andExpect { assertThat(it.response.contentAsString).contains("\"commentId\":21") }
            .andDo(
                documentApi(
                    "leaveQuestionComment",
                    CREATE_SUMMARY,
                    CREATE_DESCRIPTION,
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                        fieldWithPath("questionId").description("원 질문 id"),
                        fieldWithPath("type").description("최초 유형 MEMO"),
                        fieldWithPath("content").description("공백이 아닌 메모 본문"),
                    ),
                    successResponseFields(fieldWithPath("data.commentId").description("생성된 질문 메모 id")),
                ),
            )
    }

    @Test
    fun `질문 메모의 좋아요 상태를 토글한다`() {
        justRun {
            service.toggleType(memberId, roomId, intervieweeId, 11L, 21L, QuestionCommentType.GOOD_POINT)
        }

        mockMvc.perform(
            patch("/v1/question-comment-types/{commentId}", 21L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toggleTypeRequest("GOOD_POINT")),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "toggleQuestionCommentType",
                    TOGGLE_SUMMARY,
                    TOGGLE_DESCRIPTION,
                    pathParameters(parameterWithName("commentId").description("질문 메모 id")),
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                        fieldWithPath("questionId").description("원 질문 id"),
                        fieldWithPath("type").description("GOOD_POINT | IMPROVEMENT_POINT"),
                    ),
                    emptySuccessResponseFields(),
                ),
            )
    }

    @Test
    fun `작성한 질문 메모 본문을 수정한다`() {
        justRun { service.editComment(memberId, roomId, intervieweeId, 11L, 21L, "수정한 기록") }

        mockMvc.perform(
            patch("$basePath/{commentId}", 21L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(editCommentRequest("수정한 기록")),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "editQuestionComment",
                    EDIT_SUMMARY,
                    EDIT_DESCRIPTION,
                    pathParameters(parameterWithName("commentId").description("질문 메모 id")),
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
                        fieldWithPath("questionId").description("원 질문 id"),
                        fieldWithPath("content").description("공백이 아닌 수정 본문"),
                    ),
                    emptySuccessResponseFields(),
                ),
            )
    }

    @Test
    fun `작성한 질문 메모를 삭제한다`() {
        justRun { service.deleteComment(memberId, roomId, intervieweeId, 11L, 21L) }

        mockMvc.perform(
            delete("$basePath/{commentId}", 21L)
                .queryParam("roomId", roomId.toString())
                .queryParam("intervieweeMemberId", intervieweeId.toString())
                .queryParam("questionId", "11")
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "deleteQuestionComment",
                    DELETE_SUMMARY,
                    DELETE_DESCRIPTION,
                    pathParameters(parameterWithName("commentId").description("질문 메모 id")),
                    questionQueryParameters(),
                    emptySuccessResponseFields(),
                ),
            )
    }

    @Test
    fun `질문 메모 최초 유형이 MEMO가 아니면 E400`() {
        mockMvc.perform(
            post(basePath)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createCommentRequest("GOOD_POINT", "기록")),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "leaveQuestionComment-e400",
                    CREATE_SUMMARY,
                    CREATE_DESCRIPTION,
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { service.leaveComment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `질문 메모 커서 두 값 중 하나만 보내면 E400`() {
        mockMvc.perform(
            get(basePath)
                .queryParam("roomId", roomId.toString())
                .queryParam("intervieweeMemberId", intervieweeId.toString())
                .queryParam("questionId", "11")
                .queryParam("cursorId", "20")
                .principal(principal),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "getQuestionComments-e400",
                    GET_SUMMARY,
                    GET_DESCRIPTION,
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { facade.getComments(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `질문 메모 평가 유형이 MEMO이면 E400`() {
        mockMvc.perform(
            patch("/v1/question-comment-types/{commentId}", 21L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toggleTypeRequest("MEMO")),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "toggleQuestionCommentType-e400",
                    TOGGLE_SUMMARY,
                    TOGGLE_DESCRIPTION,
                    commentIdPathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `질문 메모 수정 본문이 공백이면 E400`() {
        mockMvc.perform(
            patch("$basePath/{commentId}", 21L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(editCommentRequest("  ")),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "editQuestionComment-e400",
                    EDIT_SUMMARY,
                    EDIT_DESCRIPTION,
                    commentIdPathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `질문 메모 조회 오류를 문서화한다`() {
        readErrors().forEach { errorType ->
            every { facade.getComments(memberId, roomId, intervieweeId, 11L, null) } throws CoreException(errorType)

            mockMvc.perform(
                get(basePath)
                    .queryParam("roomId", roomId.toString())
                    .queryParam("intervieweeMemberId", intervieweeId.toString())
                    .queryParam("questionId", "11")
                    .principal(principal),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getQuestionComments-${errorType.code.name.lowercase()}",
                        GET_SUMMARY,
                        GET_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `질문 메모 작성 오류를 문서화한다`() {
        writeErrors().forEach { errorType ->
            every {
                service.leaveComment(memberId, roomId, intervieweeId, 11L, QuestionCommentType.MEMO, "기록")
            } throws CoreException(errorType)

            mockMvc.perform(
                post(basePath)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createCommentRequest("MEMO", "기록")),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "leaveQuestionComment-${errorType.code.name.lowercase()}",
                        CREATE_SUMMARY,
                        CREATE_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `질문 메모 상태 변경 오류를 문서화한다`() {
        mutationErrors().forEach { errorType ->
            every {
                service.toggleType(memberId, roomId, intervieweeId, 11L, 21L, QuestionCommentType.GOOD_POINT)
            } throws CoreException(errorType)

            mockMvc.perform(
                patch("/v1/question-comment-types/{commentId}", 21L)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toggleTypeRequest("GOOD_POINT")),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "toggleQuestionCommentType-${errorType.code.name.lowercase()}",
                        TOGGLE_SUMMARY,
                        TOGGLE_DESCRIPTION,
                        commentIdPathParameters(),
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `질문 메모 수정 오류를 문서화한다`() {
        mutationErrors().forEach { errorType ->
            every { service.editComment(memberId, roomId, intervieweeId, 11L, 21L, "수정한 기록") } throws
                CoreException(errorType)

            mockMvc.perform(
                patch("$basePath/{commentId}", 21L)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(editCommentRequest("수정한 기록")),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "editQuestionComment-${errorType.code.name.lowercase()}",
                        EDIT_SUMMARY,
                        EDIT_DESCRIPTION,
                        commentIdPathParameters(),
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `질문 메모 삭제 오류를 문서화한다`() {
        mutationErrors().forEach { errorType ->
            every { service.deleteComment(memberId, roomId, intervieweeId, 11L, 21L) } throws
                CoreException(errorType)

            mockMvc.perform(
                delete("$basePath/{commentId}", 21L)
                    .queryParam("roomId", roomId.toString())
                    .queryParam("intervieweeMemberId", intervieweeId.toString())
                    .queryParam("questionId", "11")
                    .principal(principal),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "deleteQuestionComment-${errorType.code.name.lowercase()}",
                        DELETE_SUMMARY,
                        DELETE_DESCRIPTION,
                        commentIdPathParameters(),
                        errorResponseFields(),
                    ),
                )
        }
    }

    private fun readErrors(): List<CoreErrorType> = listOf(
        CoreErrorType.ROOM_NOT_FOUND,
        CoreErrorType.QUESTION_COMMENT_FORBIDDEN,
        CoreErrorType.QUESTION_COMMENT_NOT_VIEWABLE,
        CoreErrorType.QUESTION_NOT_FOUND,
    )

    private fun writeErrors(): List<CoreErrorType> = listOf(
        CoreErrorType.ROOM_NOT_FOUND,
        CoreErrorType.QUESTION_COMMENT_FORBIDDEN,
        CoreErrorType.QUESTION_COMMENT_NOT_EDITABLE,
        CoreErrorType.QUESTION_NOT_FOUND,
    )

    private fun mutationErrors(): List<CoreErrorType> = writeErrors() + CoreErrorType.QUESTION_COMMENT_NOT_FOUND

    private fun questionQueryParameters() = queryParameters(
        parameterWithName("roomId").description("룸 id (UUID)"),
        parameterWithName("intervieweeMemberId").description("라운드 면접자 회원 id (UUID)"),
        parameterWithName("questionId").description("원 질문 id"),
    )

    private fun commentIdPathParameters() = pathParameters(
        parameterWithName("commentId").description("질문 메모 id"),
    )

    private fun createCommentRequest(type: String, content: String): String = jsonMapper().writeValueAsString(
        mapOf(
            "roomId" to roomId,
            "intervieweeMemberId" to intervieweeId,
            "questionId" to 11L,
            "type" to type,
            "content" to content,
        ),
    )

    private fun toggleTypeRequest(type: String): String = jsonMapper().writeValueAsString(
        mapOf(
            "roomId" to roomId,
            "intervieweeMemberId" to intervieweeId,
            "questionId" to 11L,
            "type" to type,
        ),
    )

    private fun editCommentRequest(content: String): String = jsonMapper().writeValueAsString(
        mapOf(
            "roomId" to roomId,
            "intervieweeMemberId" to intervieweeId,
            "questionId" to 11L,
            "content" to content,
        ),
    )

    private companion object {
        const val GET_SUMMARY = "질문 메모 조회"
        const val GET_DESCRIPTION =
            "원 질문 메모를 오래된 순으로 조회한다. E400, E1405, E1507, E1509, E1512를 응답할 수 있다."
        const val CREATE_SUMMARY = "질문 메모 작성"
        const val CREATE_DESCRIPTION =
            "진행 중 원 질문에 최초 MEMO 기록을 남긴다. E400, E1405, E1507, E1509, E1510을 응답할 수 있다."
        const val TOGGLE_SUMMARY = "질문 메모 평가 상태 토글"
        const val TOGGLE_DESCRIPTION =
            "좋아요 또는 아쉬워요 상태를 토글한다. E400, E1405, E1507, E1509, E1510, E1511을 응답할 수 있다."
        const val EDIT_SUMMARY = "질문 메모 수정"
        const val EDIT_DESCRIPTION =
            "작성자 본인이 질문 메모를 수정한다. E400, E1405, E1507, E1509, E1510, E1511을 응답할 수 있다."
        const val DELETE_SUMMARY = "질문 메모 삭제"
        const val DELETE_DESCRIPTION =
            "작성자 본인이 질문 메모를 삭제한다. E1405, E1507, E1509, E1510, E1511을 응답할 수 있다."
    }
}
