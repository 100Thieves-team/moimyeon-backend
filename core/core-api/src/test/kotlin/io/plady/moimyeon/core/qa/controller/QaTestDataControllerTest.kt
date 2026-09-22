package io.plady.moimyeon.core.qa.controller

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.qa.QaDataCondition
import io.plady.moimyeon.core.qa.QaDeletedRows
import io.plady.moimyeon.core.qa.QaRoom
import io.plady.moimyeon.core.qa.QaTestDataService
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

class QaTestDataControllerTest : RestDocsTest() {
    private val service = mockk<QaTestDataService>()

    private val roomId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val canceledRoomId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val hostMemberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val defaultCondition = QaDataCondition(prefix = "[QA]", hostMemberId = null)

    private val devOnlyNote =
        "local·local-dev·dev 프로파일에서만 등록되는 dev 전용 Test API 다(staging·live 에는 경로가 없다). " +
            "QA 플랫폼이 테스트 데이터를 정리하는 용도이며 검증 대상 API 가 아니다. "

    private val listSummary = "[dev] QA 데이터 목록"
    private val listDescription = devOnlyNote +
        "제목이 prefix(기본 [QA])로 시작하는 룸을 soft delete 여부와 무관하게 전부 돌려준다. " +
        "prefix 가 [QA] 로 시작하지 않거나 hostMemberId 가 UUID 가 아니면 400(E400)."

    private val deleteAllSummary = "[dev] QA 데이터 일괄 삭제"
    private val deleteAllDescription = devOnlyNote +
        "제목이 prefix(기본 [QA])로 시작하는 룸 전부를 딸린 행까지 하드 삭제한다. hostMemberId 를 주면 그 회원이 방장인 룸만. " +
        "한 트랜잭션이며 응답은 테이블별 건수 합계다. prefix 가 [QA] 로 시작하지 않으면 400(E400)."

    private val deleteRoomSummary = "[dev] QA 룸 삭제"
    private val deleteRoomDescription = devOnlyNote +
        "룸 하나를 참가 신청·참여·출석·진행·질문·클로징·후기·방명록 등 룸에 매인 행까지 하드 삭제한다. " +
        "제목이 [QA] 로 시작하지 않으면 409(E2201), 룸이 없으면 404(E1405), roomId 가 UUID 가 아니면 400(E400)."

    private val resetSummary = "[dev] 테스트 계정 초기화"
    private val resetDescription = devOnlyNote +
        "회원을 룸 하나도 없는 처음 상태로 되돌린다. 방장인 [QA] 룸 전부 삭제, 이 회원의 참가 신청·참여 행 삭제(다른 회원의 룸 포함), " +
        "[QA] 룸에서 받은/쓴 후기 삭제. 회원 행·프로필·이력서는 유지한다. " +
        "방장인 룸 중 [QA] 가 아닌 것이 있으면 409(E2201)로 전체 거절, 회원이 없거나 탈퇴했으면 404(E1006), " +
        "memberId 가 UUID 가 아니면 400(E400)."

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(QaTestDataController(service), controllerAdvice = ApiControllerAdvice())
    }

    @Test
    fun `QA 룸 목록을 응답한다`() {
        every { service.getRooms(defaultCondition) } returns listOf(
            QaRoom(
                id = roomId,
                title = "[QA] 스모크 테스트 룸",
                status = RoomStatus.RECRUITING,
                hostMemberId = hostMemberId,
                createdAt = LocalDateTime.of(2026, 9, 22, 10, 0),
                applicationCount = 3,
                participantCount = 2,
            ),
            QaRoom(
                id = canceledRoomId,
                title = "[QA] 방장이 나간 룸",
                status = RoomStatus.CANCELED,
                hostMemberId = null,
                createdAt = LocalDateTime.of(2026, 9, 21, 9, 30),
                applicationCount = 1,
                participantCount = 1,
            ),
        )

        mockMvc.perform(get(QA_DATA_PATH).queryParam("prefix", "[QA]"))
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains(roomId.toString(), canceledRoomId.toString()) }
            .andDo(
                documentApi(
                    "listQaData",
                    listSummary,
                    listDescription,
                    qaDataQueryParameters(),
                    successResponseFields(
                        fieldWithPath("data.rooms").type(JsonFieldType.ARRAY).description("QA 룸 목록 (생성순)"),
                        fieldWithPath("data.rooms[].roomId").type(JsonFieldType.STRING).description("룸 id (UUID)"),
                        fieldWithPath("data.rooms[].title").type(JsonFieldType.STRING).description("룸 제목 ([QA] 로 시작)"),
                        fieldWithPath("data.rooms[].status").type(JsonFieldType.STRING)
                            .description("룸 상태 (RECRUITING | CONFIRMED | IN_PROGRESS | COMPLETED | CANCELED)"),
                        fieldWithPath("data.rooms[].hostMemberId").type(JsonFieldType.STRING).optional()
                            .description("현재 방장 회원 id (UUID). 방장이 나간 취소 룸처럼 없으면 null"),
                        fieldWithPath("data.rooms[].createdAt").type(JsonFieldType.STRING).description("생성 일시 (ISO-8601)"),
                        fieldWithPath("data.rooms[].counts.applications").type(JsonFieldType.NUMBER)
                            .description("참가 신청 행 수 (상태·soft delete 무관, 삭제 시 지워지는 행 수)"),
                        fieldWithPath("data.rooms[].counts.participants").type(JsonFieldType.NUMBER)
                            .description("참여 행 수 (방장 포함, 상태·soft delete 무관)"),
                    ),
                ),
            )
    }

    @Test
    fun `QA 마커로 시작하지 않는 prefix 로 목록을 요청하면 E400 을 응답한다`() {
        mockMvc.perform(get(QA_DATA_PATH).queryParam("prefix", "면접"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("listQaData-e400", listSummary, listDescription, errorResponseFields()))

        verify(exactly = 0) { service.getRooms(any()) }
    }

    @Test
    fun `UUID 형식이 아닌 hostMemberId 로 목록을 요청하면 E400 을 응답한다`() {
        mockMvc.perform(get(QA_DATA_PATH).queryParam("hostMemberId", "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("listQaData-e400-hostMemberId", listSummary, listDescription, errorResponseFields()))

        verify(exactly = 0) { service.getRooms(any()) }
    }

    @Test
    fun `QA 데이터를 일괄 삭제하고 건수 합계를 응답한다`() {
        val condition = QaDataCondition(prefix = "[QA] smoke-", hostMemberId = hostMemberId)
        every { service.deleteRooms(condition) } returns sampleDeleted(rooms = 2)

        mockMvc.perform(
            delete(QA_DATA_PATH)
                .queryParam("prefix", "[QA] smoke-")
                .queryParam("hostMemberId", hostMemberId.toString()),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"rooms\":2") }
            .andDo(
                documentApi(
                    "deleteQaData",
                    deleteAllSummary,
                    deleteAllDescription,
                    qaDataQueryParameters(),
                    successResponseFields(*deletedFields()),
                ),
            )
    }

    @Test
    fun `QA 마커로 시작하지 않는 prefix 로 일괄 삭제를 요청하면 E400 을 응답한다`() {
        mockMvc.perform(delete(QA_DATA_PATH).queryParam("prefix", "면접"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("deleteQaData-e400", deleteAllSummary, deleteAllDescription, errorResponseFields()))

        verify(exactly = 0) { service.deleteRooms(any()) }
    }

    @Test
    fun `UUID 형식이 아닌 hostMemberId 로 일괄 삭제를 요청하면 E400 을 응답한다`() {
        mockMvc.perform(delete(QA_DATA_PATH).queryParam("hostMemberId", "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("deleteQaData-e400-hostMemberId", deleteAllSummary, deleteAllDescription, errorResponseFields()))

        verify(exactly = 0) { service.deleteRooms(any()) }
    }

    @Test
    fun `QA 룸을 딸린 행까지 삭제하고 테이블별 건수를 응답한다`() {
        every { service.deleteRoom(roomId) } returns sampleDeleted(rooms = 1)

        mockMvc.perform(delete(ROOM_PATH, roomId))
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"rooms\":1") }
            .andDo(
                documentApi(
                    "deleteQaRoom",
                    deleteRoomSummary,
                    deleteRoomDescription,
                    pathParameters(parameterWithName("roomId").description("삭제할 룸 id (UUID)")),
                    successResponseFields(*deletedFields()),
                ),
            )
    }

    @Test
    fun `없는 룸을 삭제하면 E1405 를 응답한다`() {
        every { service.deleteRoom(roomId) } throws CoreException(CoreErrorType.ROOM_NOT_FOUND)

        mockMvc.perform(delete(ROOM_PATH, roomId))
            .andExpect(status().isNotFound)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1405\"") }
            .andDo(documentApi("deleteQaRoom-e1405", deleteRoomSummary, deleteRoomDescription, errorResponseFields()))
    }

    @Test
    fun `제목이 QA 마커로 시작하지 않는 룸을 삭제하면 E2201 을 응답한다`() {
        every { service.deleteRoom(roomId) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        mockMvc.perform(delete(ROOM_PATH, roomId))
            .andExpect(status().isConflict)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E2201\"") }
            .andDo(documentApi("deleteQaRoom-e2201", deleteRoomSummary, deleteRoomDescription, errorResponseFields()))
    }

    @Test
    fun `UUID 형식이 아닌 룸 id 로 삭제를 요청하면 E400 을 응답한다`() {
        mockMvc.perform(delete(ROOM_PATH, "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("deleteQaRoom-e400", deleteRoomSummary, deleteRoomDescription, errorResponseFields()))

        verify(exactly = 0) { service.deleteRoom(any()) }
    }

    @Test
    fun `테스트 계정을 초기화하고 삭제 건수를 응답한다`() {
        every { service.resetMember(hostMemberId) } returns sampleDeleted(rooms = 1)

        mockMvc.perform(post(RESET_PATH, hostMemberId))
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"rooms\":1") }
            .andDo(
                documentApi(
                    "resetQaMember",
                    resetSummary,
                    resetDescription,
                    pathParameters(parameterWithName("memberId").description("초기화할 테스트 계정 회원 id (UUID)")),
                    successResponseFields(*deletedFields()),
                ),
            )
    }

    @Test
    fun `없는 회원을 초기화하면 E1006 을 응답한다`() {
        every { service.resetMember(hostMemberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        mockMvc.perform(post(RESET_PATH, hostMemberId))
            .andExpect(status().isNotFound)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1006\"") }
            .andDo(documentApi("resetQaMember-e1006", resetSummary, resetDescription, errorResponseFields()))
    }

    @Test
    fun `방장인 룸 중 QA 마커가 없는 룸이 있으면 초기화를 E2201 로 거절한다`() {
        every { service.resetMember(hostMemberId) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        mockMvc.perform(post(RESET_PATH, hostMemberId))
            .andExpect(status().isConflict)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E2201\"") }
            .andDo(documentApi("resetQaMember-e2201", resetSummary, resetDescription, errorResponseFields()))
    }

    @Test
    fun `UUID 형식이 아닌 회원 id 로 초기화를 요청하면 E400 을 응답한다`() {
        mockMvc.perform(post(RESET_PATH, "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("resetQaMember-e400", resetSummary, resetDescription, errorResponseFields()))

        verify(exactly = 0) { service.resetMember(any()) }
    }

    private fun qaDataQueryParameters() = queryParameters(
        parameterWithName("prefix").optional()
            .description("룸 제목 접두 (기본 [QA]). [QA] 로 시작해야 하며 더 좁힐 수만 있다 (예: \"[QA] smoke-\")"),
        parameterWithName("hostMemberId").optional()
            .description("이 회원이 현재 방장인 룸만 (UUID, 선택). 목록 조회에서도 같은 필터로 동작한다"),
    )

    private fun sampleDeleted(rooms: Int) = QaDeletedRows(
        guestbookPosts = 2,
        guestbooks = 1,
        reviewTags = 4,
        reviews = 2,
        reviewSkips = 1,
        attendances = 3,
        questionVotes = 6,
        closingResponses = 3,
        questionComments = 2,
        answerSummaries = 2,
        questions = 5,
        roundFeedbacks = 2,
        roundAssignments = 0,
        interviewRounds = 0,
        interviewPlans = 0,
        resumeSubmissions = 3,
        participants = 3,
        applications = 4,
        roomStatusLogs = 2,
        rooms = rooms,
    )

    private fun deletedFields(): Array<FieldDescriptor> = arrayOf(
        fieldWithPath("data.deleted").type(JsonFieldType.OBJECT).description("테이블별 하드 삭제 건수. 키는 항상 전부 내려간다(0 포함)"),
        fieldWithPath("data.deleted.rooms").type(JsonFieldType.NUMBER).description("room"),
        fieldWithPath("data.deleted.roomStatusLogs").type(JsonFieldType.NUMBER).description("room_status_log (상태 전이 이력)"),
        fieldWithPath("data.deleted.applications").type(JsonFieldType.NUMBER).description("room_application (참가 신청)"),
        fieldWithPath("data.deleted.participants").type(JsonFieldType.NUMBER).description("participation (참여, 방장 포함)"),
        fieldWithPath("data.deleted.resumeSubmissions").type(JsonFieldType.NUMBER).description("resume_submission (신청 시 제출한 이력서)"),
        fieldWithPath("data.deleted.interviewPlans").type(JsonFieldType.NUMBER).description("interview_plan (진행 계획)"),
        fieldWithPath("data.deleted.interviewRounds").type(JsonFieldType.NUMBER).description("interview_round (진행 라운드)"),
        fieldWithPath("data.deleted.roundAssignments").type(JsonFieldType.NUMBER).description("round_assignment (라운드 역할 배정)"),
        fieldWithPath("data.deleted.roundFeedbacks").type(JsonFieldType.NUMBER).description("round_feedback (라운드 피드백)"),
        fieldWithPath("data.deleted.questions").type(JsonFieldType.NUMBER).description("question (질문 · 꼬리질문)"),
        fieldWithPath("data.deleted.answerSummaries").type(JsonFieldType.NUMBER).description("answer_summary (답변 요약)"),
        fieldWithPath("data.deleted.questionComments").type(JsonFieldType.NUMBER).description("question_comment (질문 코멘트)"),
        fieldWithPath("data.deleted.closingResponses").type(JsonFieldType.NUMBER).description("closing_response (클로징 응답)"),
        fieldWithPath("data.deleted.questionVotes").type(JsonFieldType.NUMBER).description("question_vote (클로징 질문 평가)"),
        fieldWithPath("data.deleted.attendances").type(JsonFieldType.NUMBER).description("attendance (출석)"),
        fieldWithPath("data.deleted.reviews").type(JsonFieldType.NUMBER).description("review (후기)"),
        fieldWithPath("data.deleted.reviewTags").type(JsonFieldType.NUMBER).description("review_tag (후기 태그)"),
        fieldWithPath("data.deleted.reviewSkips").type(JsonFieldType.NUMBER).description("review_skip (후기 건너뜀)"),
        fieldWithPath("data.deleted.guestbooks").type(JsonFieldType.NUMBER).description("room_guestbook (방명록)"),
        fieldWithPath("data.deleted.guestbookPosts").type(JsonFieldType.NUMBER).description("guestbook_post (방명록 댓글)"),
        fieldWithPath("data.deleted.total").type(JsonFieldType.NUMBER).description("위 건수의 합"),
    )
}

private const val QA_DATA_PATH = "/v1/dev/qa-data"
private const val ROOM_PATH = "/v1/dev/rooms/{roomId}"
private const val RESET_PATH = "/v1/dev/members/{memberId}/reset"
