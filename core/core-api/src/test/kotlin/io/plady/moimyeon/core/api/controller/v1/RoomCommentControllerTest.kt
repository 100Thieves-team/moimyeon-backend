package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.RoomCommentCursorToken
import io.plady.moimyeon.core.api.controller.v1.response.RoomCommentAuthorResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomCommentCreatedResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomCommentResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomCommentsResponse
import io.plady.moimyeon.core.api.facade.RoomCommentFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.roomcomment.RoomCommentCursor
import io.plady.moimyeon.core.domain.roomcomment.RoomCommentService
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID

class RoomCommentControllerTest : RestDocsTest() {
    private lateinit var facade: RoomCommentFacade
    private lateinit var service: RoomCommentService

    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000461")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val hostMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val leftMemberId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val principal = Principal { memberId.toString() }
    private val basePath = "/v1/rooms/{roomId}/comments"

    @BeforeEach
    fun setUpController() {
        facade = mockk()
        service = mockk()
        mockMvc = mockController(
            RoomCommentController(facade, service),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `방명록 글 목록을 최신순으로 조회한다`() {
        val cursor = RoomCommentCursor(LocalDateTime.of(2026, 7, 21, 14, 40), 45L)
        val nextCursor = RoomCommentCursorToken.encode(RoomCommentCursor(LocalDateTime.of(2026, 7, 21, 14, 14), 40L))
        every { facade.getComments(memberId, roomId, cursor, 20) } returns RoomCommentsResponse(
            comments = listOf(
                RoomCommentResponse(
                    commentId = 44L,
                    isDeleted = false,
                    author = RoomCommentAuthorResponse(hostMemberId, "꼼꼼한 여우 12", isHost = true, hasLeft = false),
                    content = "그럼 각자 이력서 기준으로 예상 질문 3개씩 준비해와요.",
                    createdAt = LocalDateTime.of(2026, 7, 21, 14, 31),
                    isMine = false,
                ),
                RoomCommentResponse(
                    commentId = 43L,
                    isDeleted = true,
                    author = null,
                    content = null,
                    createdAt = LocalDateTime.of(2026, 7, 21, 14, 22),
                    isMine = false,
                ),
                RoomCommentResponse(
                    commentId = 42L,
                    isDeleted = false,
                    author = RoomCommentAuthorResponse(leftMemberId, "성실한 사슴 03", isHost = false, hasLeft = true),
                    content = "좋아요. 자기소개는 각자 3분 정도 준비하면 될까요?",
                    createdAt = LocalDateTime.of(2026, 7, 21, 14, 20),
                    isMine = false,
                ),
                RoomCommentResponse(
                    commentId = 41L,
                    isDeleted = false,
                    author = RoomCommentAuthorResponse(memberId, "든든한 곰 21", isHost = false, hasLeft = false),
                    content = "네 좋습니다. 이력서 기준 예상 질문도 각자 정리해오면 좋을 것 같아요.",
                    createdAt = LocalDateTime.of(2026, 7, 21, 14, 15),
                    isMine = true,
                ),
            ),
            writable = true,
            readOnlyAt = LocalDateTime.of(2026, 7, 22, 18, 40),
            nextCursor = nextCursor,
        )

        mockMvc.perform(
            get(basePath, roomId)
                .queryParam("cursor", RoomCommentCursorToken.encode(cursor))
                .queryParam("size", "20")
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"isMine\":true") }
            .andExpect { assertThat(it.response.contentAsString).contains("\"isDeleted\":true") }
            .andDo(
                documentApi(
                    "getRoomComments",
                    GET_SUMMARY,
                    GET_DESCRIPTION,
                    pathParameters(parameterWithName("roomId").description("룸 id (UUID)")),
                    queryParameters(
                        parameterWithName("cursor").optional()
                            .description("이전 응답의 nextCursor 를 그대로 되돌려주는 불투명 토큰. 첫 페이지는 생략"),
                        parameterWithName("size").optional().description("페이지 크기 (기본 20, 최대 50)"),
                    ),
                    successResponseFields(
                        fieldWithPath("data.comments").description("최신순 글 목록"),
                        fieldWithPath("data.comments[].commentId").description("글 id"),
                        fieldWithPath("data.comments[].isDeleted")
                            .description("삭제된 글 여부. true 면 tombstone - 작성자·내용이 가려진다"),
                        fieldWithPath("data.comments[].author").type(JsonFieldType.OBJECT).optional()
                            .description("작성자. 삭제된 글은 null"),
                        fieldWithPath("data.comments[].author.memberId").type(JsonFieldType.STRING).optional()
                            .description("작성자 회원 id"),
                        fieldWithPath("data.comments[].author.nickname").type(JsonFieldType.STRING).optional()
                            .description("작성자 닉네임. 탈퇴 회원은 \"탈퇴한 회원\""),
                        fieldWithPath("data.comments[].author.isHost").type(JsonFieldType.BOOLEAN).optional()
                            .description("현재 방장 여부 (위임되면 새 방장 글만 true)"),
                        fieldWithPath("data.comments[].author.hasLeft").type(JsonFieldType.BOOLEAN).optional()
                            .description("퇴장 여부 - (퇴장) 표시"),
                        fieldWithPath("data.comments[].content").type(JsonFieldType.STRING).optional()
                            .description("글 내용. 삭제된 글은 null"),
                        fieldWithPath("data.comments[].createdAt").description("작성 시각"),
                        fieldWithPath("data.comments[].isMine").description("내가 쓴 글 여부 - '나' 뱃지·삭제 버튼 판정"),
                        fieldWithPath("data.writable").description("작성 가능 여부. false 면 읽기 전용"),
                        fieldWithPath("data.readOnlyAt").type(JsonFieldType.STRING).optional()
                            .description("읽기 전용 전환(예정) 시각. writable=true 와 함께 오면 전환 예고 배너 조합, 활성 룸은 null"),
                        fieldWithPath("data.nextCursor").type(JsonFieldType.STRING).optional()
                            .description("다음 페이지 커서 토큰. 마지막 페이지면 null"),
                    ),
                ),
            )
    }

    @Test
    fun `참여자가 아니면 목록 조회는 E1419 를 응답한다`() {
        every { facade.getComments(memberId, roomId, null, 20) } throws
            CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        mockMvc.perform(get(basePath, roomId).principal(principal))
            .andExpect(status().isForbidden)
            .andExpect { assertThat(it.response.contentAsString).contains("E1419") }
            .andDo(documentApi("getRoomComments-e1419", GET_SUMMARY, GET_DESCRIPTION, errorResponseFields()))
    }

    @Test
    fun `깨진 커서 토큰이면 E400 을 응답한다`() {
        mockMvc.perform(
            get(basePath, roomId)
                .queryParam("cursor", "broken-token")
                .principal(principal),
        )
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("E400") }
            .andDo(documentApi("getRoomComments-e400", GET_SUMMARY, GET_DESCRIPTION, errorResponseFields()))
        verify(exactly = 0) { facade.getComments(any(), any(), any(), any()) }
    }

    @Test
    fun `방명록 글을 작성한다`() {
        every { facade.leaveComment(memberId, roomId, "좋아요. 자기소개는 각자 3분 정도 준비하면 될까요?") } returns
            RoomCommentCreatedResponse(commentId = 42L, createdAt = LocalDateTime.of(2026, 7, 21, 14, 31, 2))

        mockMvc.perform(
            post(basePath, roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content": "좋아요. 자기소개는 각자 3분 정도 준비하면 될까요?"}"""),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"commentId\":42") }
            .andDo(
                documentApi(
                    "createRoomComment",
                    CREATE_SUMMARY,
                    CREATE_DESCRIPTION,
                    pathParameters(parameterWithName("roomId").description("룸 id (UUID)")),
                    requestFields(fieldWithPath("content").description("글 내용 (trim 후 1~1000자, 텍스트만)")),
                    successResponseFields(
                        fieldWithPath("data.commentId").description("생성된(또는 멱등 반환된 기존) 글 id"),
                        fieldWithPath("data.createdAt").description("작성 시각"),
                    ),
                ),
            )
    }

    @Test
    fun `내용이 1000자를 넘으면 E400 을 응답한다`() {
        mockMvc.perform(
            post(basePath, roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content": "${"가".repeat(1001)}"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("E400") }
            .andDo(documentApi("createRoomComment-e400", CREATE_SUMMARY, CREATE_DESCRIPTION, errorResponseFields()))
        verify(exactly = 0) { facade.leaveComment(any(), any(), any()) }
    }

    @Test
    fun `참여자가 아니면 작성은 E1419 를 응답한다`() {
        every { facade.leaveComment(memberId, roomId, "몰래 등록") } throws
            CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        mockMvc.perform(
            post(basePath, roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content": "몰래 등록"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect { assertThat(it.response.contentAsString).contains("E1419") }
            .andDo(documentApi("createRoomComment-e1419", CREATE_SUMMARY, CREATE_DESCRIPTION, errorResponseFields()))
    }

    @Test
    fun `읽기 전용으로 전환된 방명록에 작성하면 E2101 을 응답한다`() {
        every { facade.leaveComment(memberId, roomId, "늦은 글") } throws
            CoreException(CoreErrorType.ROOM_COMMENT_READ_ONLY)

        mockMvc.perform(
            post(basePath, roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content": "늦은 글"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect { assertThat(it.response.contentAsString).contains("E2101") }
            .andDo(documentApi("createRoomComment-e2101", CREATE_SUMMARY, CREATE_DESCRIPTION, errorResponseFields()))
    }

    @Test
    fun `내 글을 삭제한다`() {
        justRun { service.deleteComment(memberId, roomId, 42L) }

        mockMvc.perform(delete("$basePath/{commentId}", roomId, 42L).principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "deleteRoomComment",
                    DELETE_SUMMARY,
                    DELETE_DESCRIPTION,
                    pathParameters(
                        parameterWithName("roomId").description("룸 id (UUID)"),
                        parameterWithName("commentId").description("글 id"),
                    ),
                    emptySuccessResponseFields(),
                ),
            )
    }

    @Test
    fun `참여자가 아니면 삭제는 E1419 를 응답한다`() {
        every { service.deleteComment(memberId, roomId, 42L) } throws
            CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        mockMvc.perform(delete("$basePath/{commentId}", roomId, 42L).principal(principal))
            .andExpect(status().isForbidden)
            .andExpect { assertThat(it.response.contentAsString).contains("E1419") }
            .andDo(documentApi("deleteRoomComment-e1419", DELETE_SUMMARY, DELETE_DESCRIPTION, errorResponseFields()))
    }

    @Test
    fun `남의 글을 삭제하면 E2102 를 응답한다`() {
        every { service.deleteComment(memberId, roomId, 44L) } throws
            CoreException(CoreErrorType.ROOM_COMMENT_NOT_MINE)

        mockMvc.perform(delete("$basePath/{commentId}", roomId, 44L).principal(principal))
            .andExpect(status().isForbidden)
            .andExpect { assertThat(it.response.contentAsString).contains("E2102") }
            .andDo(documentApi("deleteRoomComment-e2102", DELETE_SUMMARY, DELETE_DESCRIPTION, errorResponseFields()))
    }

    @Test
    fun `없는 글을 삭제하면 E2103 을 응답한다`() {
        every { service.deleteComment(memberId, roomId, 999L) } throws
            CoreException(CoreErrorType.ROOM_COMMENT_NOT_FOUND)

        mockMvc.perform(delete("$basePath/{commentId}", roomId, 999L).principal(principal))
            .andExpect(status().isNotFound)
            .andExpect { assertThat(it.response.contentAsString).contains("E2103") }
            .andDo(documentApi("deleteRoomComment-e2103", DELETE_SUMMARY, DELETE_DESCRIPTION, errorResponseFields()))
    }

    @Test
    fun `읽기 전용으로 전환된 방명록에서 삭제하면 E2101 을 응답한다`() {
        every { service.deleteComment(memberId, roomId, 42L) } throws
            CoreException(CoreErrorType.ROOM_COMMENT_READ_ONLY)

        mockMvc.perform(delete("$basePath/{commentId}", roomId, 42L).principal(principal))
            .andExpect(status().isConflict)
            .andExpect { assertThat(it.response.contentAsString).contains("E2101") }
            .andDo(documentApi("deleteRoomComment-e2101", DELETE_SUMMARY, DELETE_DESCRIPTION, errorResponseFields()))
    }

    companion object {
        private const val GET_SUMMARY = "룸 방명록 글 목록 조회"
        private const val GET_DESCRIPTION =
            "룸 참여자(방장 포함)가 방명록 글을 최신순으로 조회한다. 커서는 불투명 토큰으로, " +
                "이전 응답의 nextCursor 를 그대로 되돌려준다. writable=false 면 읽기 전용이고, " +
                "writable=true 인데 readOnlyAt 이 있으면 곧 읽기 전용으로 바뀐다는 예고다. " +
                "삭제된 글은 isDeleted=true tombstone 으로 남고 작성자·내용이 가려진다. " +
                "비참여자·신청자·퇴장자·존재하지 않는 룸은 403 E1419 로 끊는다(존재 비공개). " +
                "깨진 커서 토큰·size 범위(1~50) 위반은 400 E400."
        private const val CREATE_SUMMARY = "룸 방명록 글 작성"
        private const val CREATE_DESCRIPTION =
            "룸 참여자가 텍스트 글을 남긴다(trim 후 1~1000자). 직전 글과 같은 내용을 10초 안에 " +
                "다시 보내면 새로 만들지 않고 기존 글을 돌려준다(멱등 - 에러가 아니라 200). " +
                "400 E400 내용 규칙 위반, 403 E1419 비참여자, 409 E2101 읽기 전용 전환 후 작성."
        private const val DELETE_SUMMARY = "룸 방명록 글 삭제"
        private const val DELETE_DESCRIPTION =
            "내가 쓴 글을 소프트 삭제한다. 목록에서 빠지지 않고 tombstone 으로 남는다. " +
                "이미 삭제된 글의 재삭제는 200(멱등). 403 E1419 비참여자, 403 E2102 내 글 아님, " +
                "404 E2103 글 없음, 409 E2101 읽기 전용 전환 후에는 삭제도 불가."
    }
}
