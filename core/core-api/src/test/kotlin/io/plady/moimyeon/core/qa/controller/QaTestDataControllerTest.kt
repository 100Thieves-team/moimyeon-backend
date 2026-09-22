package io.plady.moimyeon.core.qa.controller

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.auth.DevAccessTokenIssuer
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.qa.QaData
import io.plady.moimyeon.core.qa.QaDataCondition
import io.plady.moimyeon.core.qa.QaDeletedRows
import io.plady.moimyeon.core.qa.QaMember
import io.plady.moimyeon.core.qa.QaResumeSummary
import io.plady.moimyeon.core.qa.QaRoom
import io.plady.moimyeon.core.qa.QaRoomSchedule
import io.plady.moimyeon.core.qa.QaTestDataService
import io.plady.moimyeon.core.qa.controller.request.CompleteQaResumeSummaryRequest
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
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

class QaTestDataControllerTest : RestDocsTest() {
    private val service = mockk<QaTestDataService>()
    private val devAccessTokenIssuer = mockk<DevAccessTokenIssuer>()

    private val roomId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val canceledRoomId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val hostMemberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val resumeId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val qaMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
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

    private val rescheduleSummary = "[dev] QA 룸 시작 시각 변경"
    private val rescheduleDescription = devOnlyNote +
        "[QA] 룸의 진행 시작 시각(startAt)을 값 규칙 검증 없이 바꾼다. 상태는 바꾸지 않는다. " +
        "용도: 진행 확정(CONFIRMED) 뒤 시작 시각을 과거로 옮기면 공개 API 로 진행 시작·클로징·종료·후기까지 실제 코드 경로로 갈 수 있다. " +
        "제목이 [QA] 로 시작하지 않으면 409(E2201), 룸이 없으면 404(E1405), startAt 이 없거나 형식이 틀리면 400(E400)."

    private val createMemberSummary = "[dev] QA 테스트 회원 생성"
    private val createMemberDescription = devOnlyNote +
        "Google OAuth 없이 테스트 회원을 만든다. 닉네임 자동 부여·필수 약관 동의·빈 프로필 생성까지 실제 가입과 같은 경로를 탄다. " +
        "이메일은 qa-{uuid}@qa.moimyeon.test, 소셜 계정 식별자는 qa-{uuid} 다. 응답의 accessToken 으로 바로 API 를 호출할 수 있다. " +
        "생성된 회원은 삭제 API 가 없으며 테스트 계정 초기화(reset)로 정리한다."

    private val resumeSummarySummary = "[dev] 이력서 AI 요약 완료 강제"
    private val resumeSummaryDescription = devOnlyNote +
        "이름이 [QA] 로 시작하는 이력서의 AI 요약을 Bedrock 호출 없이 완료(DONE) 상태로 만든다(이름은 이력서 이름 변경 API 로 바꿀 수 있다). " +
        "실패(FAILED)·생성 중(PROCESSING)이면 주어진 요약문으로 완료하고, 이미 완료면 그대로 둔다. " +
        "회원에게 기본 이력서가 없으면 이 이력서를 기본으로 지정한다(실제 요약 완료와 같은 규칙). " +
        "이름이 [QA] 로 시작하지 않으면 409(E2201), 이력서가 없거나 삭제됐으면 404(E1010), 요약문이 공백이거나 1000자를 넘으면 400(E400)."

    private val deleteMemberSummary = "[dev] QA 테스트 회원 삭제"
    private val deleteMemberDescription = devOnlyNote +
        "테스트 회원 생성 API 로 만든 회원(이메일 @qa.moimyeon.test, 소셜 식별자 qa-)만 하드 삭제한다. " +
        "먼저 테스트 계정 초기화 규칙(방장인 [QA] 룸·참여·신청·[QA] 룸 후기 삭제)을 적용한 뒤, 이 회원이 남긴 행(질문·코멘트·요약·클로징·" +
        "라운드 피드백·방명록·출석·후기)과 회원 소유 행(이력서·프로필·약관 동의·토큰·소셜 계정)을 지우고 회원 행을 지운다. " +
        "QA 생성 회원이 아니거나 방장인 룸 중 [QA] 가 아닌 룸이 있으면 409(E2201), 회원이 없으면 404(E1006), memberId 가 UUID 가 아니면 400(E400)."

    private val resetSummary = "[dev] 테스트 계정 초기화"
    private val resetDescription = devOnlyNote +
        "회원을 룸 하나도 없는 처음 상태로 되돌린다. 방장인 [QA] 룸 전부 삭제, 이 회원의 참가 신청·참여 행 삭제(다른 회원의 룸 포함), " +
        "[QA] 룸에서 받은/쓴 후기 삭제. 회원 행·프로필·이력서는 유지한다. " +
        "방장인 룸 중 [QA] 가 아닌 것이 있으면 409(E2201)로 전체 거절, 회원이 없거나 탈퇴했으면 404(E1006), " +
        "memberId 가 UUID 가 아니면 400(E400)."

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(QaTestDataController(service, devAccessTokenIssuer), controllerAdvice = ApiControllerAdvice())
    }

    @Test
    fun `QA 룸 목록을 응답한다`() {
        every { service.getQaData(defaultCondition) } returns QaData(
            rooms = listOf(
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
            ),
            members = listOf(QaMember(id = qaMemberId, nickname = "테스트닉네임", email = "qa-abc@qa.moimyeon.test")),
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
                        fieldWithPath("data.members").type(JsonFieldType.ARRAY)
                            .description("테스트 회원 생성 API 로 만든 QA 회원 목록 (생성순). prefix 필터와 무관하다"),
                        fieldWithPath("data.members[].memberId").type(JsonFieldType.STRING).description("회원 id (UUID)"),
                        fieldWithPath("data.members[].nickname").type(JsonFieldType.STRING).description("닉네임"),
                        fieldWithPath("data.members[].email").type(JsonFieldType.STRING).description("테스트 이메일 (qa-{uuid}@qa.moimyeon.test)"),
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

        verify(exactly = 0) { service.getQaData(any()) }
    }

    @Test
    fun `UUID 형식이 아닌 hostMemberId 로 목록을 요청하면 E400 을 응답한다`() {
        mockMvc.perform(get(QA_DATA_PATH).queryParam("hostMemberId", "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("listQaData-e400-hostMemberId", listSummary, listDescription, errorResponseFields()))

        verify(exactly = 0) { service.getQaData(any()) }
    }

    @Test
    fun `QA 데이터를 일괄 삭제하고 건수 합계를 응답한다`() {
        val condition = QaDataCondition(prefix = "[QA] smoke-", hostMemberId = hostMemberId, includeMembers = true)
        every { service.deleteQaData(condition) } returns sampleDeleted(rooms = 2).copy(members = 2, profiles = 2, socialAccounts = 2)

        mockMvc.perform(
            delete(QA_DATA_PATH)
                .queryParam("prefix", "[QA] smoke-")
                .queryParam("hostMemberId", hostMemberId.toString())
                .queryParam("includeMembers", "true"),
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

        verify(exactly = 0) { service.deleteQaData(any()) }
    }

    @Test
    fun `UUID 형식이 아닌 hostMemberId 로 일괄 삭제를 요청하면 E400 을 응답한다`() {
        mockMvc.perform(delete(QA_DATA_PATH).queryParam("hostMemberId", "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("deleteQaData-e400-hostMemberId", deleteAllSummary, deleteAllDescription, errorResponseFields()))

        verify(exactly = 0) { service.deleteQaData(any()) }
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
    fun `QA 생성 회원을 딸린 행까지 삭제하고 건수를 응답한다`() {
        every { service.deleteMember(qaMemberId) } returns sampleDeleted(rooms = 1).copy(
            resumes = 1,
            profiles = 1,
            termsAgreements = 2,
            refreshTokens = 1,
            webPushSubscriptions = 0,
            socialAccounts = 1,
            members = 1,
        )

        mockMvc.perform(delete(MEMBER_PATH, qaMemberId))
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"members\":1") }
            .andDo(
                documentApi(
                    "deleteQaMember",
                    deleteMemberSummary,
                    deleteMemberDescription,
                    pathParameters(parameterWithName("memberId").description("삭제할 QA 생성 회원 id (UUID)")),
                    successResponseFields(*deletedFields()),
                ),
            )
    }

    @Test
    fun `없는 회원을 삭제하면 E1006 을 응답한다`() {
        every { service.deleteMember(qaMemberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        mockMvc.perform(delete(MEMBER_PATH, qaMemberId))
            .andExpect(status().isNotFound)
            .andDo(documentApi("deleteQaMember-e1006", deleteMemberSummary, deleteMemberDescription, errorResponseFields()))
    }

    @Test
    fun `QA 생성 회원이 아니면 삭제를 E2201 로 거절한다`() {
        every { service.deleteMember(qaMemberId) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        mockMvc.perform(delete(MEMBER_PATH, qaMemberId))
            .andExpect(status().isConflict)
            .andDo(documentApi("deleteQaMember-e2201", deleteMemberSummary, deleteMemberDescription, errorResponseFields()))
    }

    @Test
    fun `UUID 형식이 아닌 회원 id 로 삭제를 요청하면 E400 을 응답한다`() {
        mockMvc.perform(delete(MEMBER_PATH, "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andDo(documentApi("deleteQaMember-e400", deleteMemberSummary, deleteMemberDescription, errorResponseFields()))

        verify(exactly = 0) { service.deleteMember(any()) }
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

    @Test
    fun `QA 룸의 시작 시각을 옮기고 현재 상태와 함께 응답한다`() {
        val startAt = LocalDateTime.of(2026, 9, 1, 9, 0)
        every { service.rescheduleRoom(roomId, startAt) } returns QaRoomSchedule(roomId, RoomStatus.CONFIRMED, startAt)

        mockMvc.perform(
            post(SCHEDULE_PATH, roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"startAt":"2026-09-01T09:00:00"}"""),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"status\":\"CONFIRMED\"") }
            .andDo(
                documentApi(
                    "rescheduleQaRoom",
                    rescheduleSummary,
                    rescheduleDescription,
                    pathParameters(parameterWithName("roomId").description("시작 시각을 바꿀 [QA] 룸 id (UUID)")),
                    requestFields(
                        fieldWithPath("startAt").type(JsonFieldType.STRING)
                            .description("새 진행 시작 시각 (ISO-8601, 서버 로컬). 과거·미래 모두 허용하며 값 규칙 검증을 우회한다"),
                    ),
                    successResponseFields(
                        fieldWithPath("data.roomId").type(JsonFieldType.STRING).description("룸 id"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING)
                            .description("현재 룸 상태 (RECRUITING | CONFIRMED | IN_PROGRESS | COMPLETED | CANCELED). 이 API 는 상태를 바꾸지 않는다"),
                        fieldWithPath("data.startAt").type(JsonFieldType.STRING).description("반영된 진행 시작 시각"),
                    ),
                ),
            )
    }

    @Test
    fun `startAt 없이 시작 시각 변경을 요청하면 E400 을 응답한다`() {
        mockMvc.perform(post(SCHEDULE_PATH, roomId).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("rescheduleQaRoom-e400", rescheduleSummary, rescheduleDescription, errorResponseFields()))

        verify(exactly = 0) { service.rescheduleRoom(any(), any()) }
    }

    @Test
    fun `없는 룸의 시작 시각을 바꾸면 E1405 를 응답한다`() {
        every { service.rescheduleRoom(roomId, any()) } throws CoreException(CoreErrorType.ROOM_NOT_FOUND)

        mockMvc.perform(
            post(SCHEDULE_PATH, roomId).contentType(MediaType.APPLICATION_JSON).content("""{"startAt":"2026-09-01T09:00:00"}"""),
        )
            .andExpect(status().isNotFound)
            .andDo(documentApi("rescheduleQaRoom-e1405", rescheduleSummary, rescheduleDescription, errorResponseFields()))
    }

    @Test
    fun `QA 마커가 없는 룸의 시작 시각을 바꾸면 E2201 을 응답한다`() {
        every { service.rescheduleRoom(roomId, any()) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        mockMvc.perform(
            post(SCHEDULE_PATH, roomId).contentType(MediaType.APPLICATION_JSON).content("""{"startAt":"2026-09-01T09:00:00"}"""),
        )
            .andExpect(status().isConflict)
            .andDo(documentApi("rescheduleQaRoom-e2201", rescheduleSummary, rescheduleDescription, errorResponseFields()))
    }

    @Test
    fun `테스트 회원을 만들고 dev 액세스 토큰을 응답한다`() {
        every { service.createMember() } returns QaMember(id = hostMemberId, nickname = "테스트닉네임", email = "qa-abc@qa.moimyeon.test")
        every { devAccessTokenIssuer.issue(hostMemberId) } returns "access-token"

        mockMvc.perform(post(MEMBERS_PATH))
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"accessToken\":\"access-token\"") }
            .andDo(
                documentApi(
                    "createQaMember",
                    createMemberSummary,
                    createMemberDescription,
                    successResponseFields(
                        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("생성된 회원 id (UUID)"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("자동 부여된 닉네임"),
                        fieldWithPath("data.email").type(JsonFieldType.STRING)
                            .description("테스트 이메일 (qa-{uuid}@qa.moimyeon.test). 실제 수신되지 않는다"),
                        fieldWithPath("data.accessToken").type(JsonFieldType.STRING)
                            .description("이 회원의 만료 없는 개발용 액세스 토큰 (dev-sessions 와 같은 토큰)"),
                    ),
                ),
            )
    }

    @Test
    fun `이력서 요약을 완료 상태로 만들고 결과를 응답한다`() {
        every { service.completeResumeSummary(resumeId, "[QA] 요약") } returns QaResumeSummary(
            resumeId = resumeId,
            memberId = hostMemberId,
            status = ResumeSummaryStatus.DONE,
            content = "[QA] 요약",
            isDefault = true,
        )

        mockMvc.perform(
            post(RESUME_SUMMARY_PATH, resumeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"summary":"[QA] 요약"}"""),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"status\":\"DONE\"") }
            .andDo(
                documentApi(
                    "completeQaResumeSummary",
                    resumeSummarySummary,
                    resumeSummaryDescription,
                    pathParameters(parameterWithName("resumeId").description("요약을 완료 처리할 이력서 id (UUID)")),
                    requestFields(
                        fieldWithPath("summary").type(JsonFieldType.STRING).optional()
                            .description("저장할 요약문 (1~1000자, 선택). 본문을 생략하면 고정 문구를 저장한다"),
                    ),
                    successResponseFields(*resumeSummaryFields()),
                ),
            )
    }

    @Test
    fun `본문 없이 요약 완료를 요청하면 고정 문구로 완료한다`() {
        every { service.completeResumeSummary(resumeId, CompleteQaResumeSummaryRequest.DEFAULT_SUMMARY) } returns QaResumeSummary(
            resumeId = resumeId,
            memberId = hostMemberId,
            status = ResumeSummaryStatus.DONE,
            content = CompleteQaResumeSummaryRequest.DEFAULT_SUMMARY,
            isDefault = false,
        )

        mockMvc.perform(post(RESUME_SUMMARY_PATH, resumeId))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "completeQaResumeSummary-default",
                    resumeSummarySummary,
                    resumeSummaryDescription,
                    successResponseFields(*resumeSummaryFields()),
                ),
            )

        verify(exactly = 1) { service.completeResumeSummary(resumeId, CompleteQaResumeSummaryRequest.DEFAULT_SUMMARY) }
    }

    @Test
    fun `없는 이력서의 요약을 완료하면 E1010 을 응답한다`() {
        every { service.completeResumeSummary(resumeId, any()) } throws CoreException(CoreErrorType.RESUME_NOT_FOUND)

        mockMvc.perform(post(RESUME_SUMMARY_PATH, resumeId))
            .andExpect(status().isNotFound)
            .andDo(documentApi("completeQaResumeSummary-e1010", resumeSummarySummary, resumeSummaryDescription, errorResponseFields()))
    }

    @Test
    fun `이름이 QA 마커로 시작하지 않는 이력서의 요약을 완료하면 E2201 을 응답한다`() {
        every { service.completeResumeSummary(resumeId, any()) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        mockMvc.perform(post(RESUME_SUMMARY_PATH, resumeId))
            .andExpect(status().isConflict)
            .andDo(documentApi("completeQaResumeSummary-e2201", resumeSummarySummary, resumeSummaryDescription, errorResponseFields()))
    }

    @Test
    fun `빈 요약문으로 완료를 요청하면 E400 을 응답한다`() {
        mockMvc.perform(
            post(RESUME_SUMMARY_PATH, resumeId).contentType(MediaType.APPLICATION_JSON).content("""{"summary":"   "}"""),
        )
            .andExpect(status().isBadRequest)
            .andDo(documentApi("completeQaResumeSummary-e400", resumeSummarySummary, resumeSummaryDescription, errorResponseFields()))

        verify(exactly = 0) { service.completeResumeSummary(any(), any()) }
    }

    private fun resumeSummaryFields(): Array<FieldDescriptor> = arrayOf(
        fieldWithPath("data.resumeId").type(JsonFieldType.STRING).description("이력서 id"),
        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("이력서 소유 회원 id"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("요약 상태 (항상 DONE)"),
        fieldWithPath("data.summary").type(JsonFieldType.STRING).description("저장된 요약문"),
        fieldWithPath("data.isDefault").type(JsonFieldType.BOOLEAN)
            .description("기본 이력서 여부. 회원에게 기본 이력서가 없었으면 이 이력서가 기본이 된다"),
    )

    private fun qaDataQueryParameters() = queryParameters(
        parameterWithName("prefix").optional()
            .description("룸 제목 접두 (기본 [QA]). [QA] 로 시작해야 하며 더 좁힐 수만 있다 (예: \"[QA] smoke-\")"),
        parameterWithName("hostMemberId").optional()
            .description("이 회원이 현재 방장인 룸만 (UUID, 선택). 목록 조회에서도 같은 필터로 동작한다"),
        parameterWithName("includeMembers").optional()
            .description(
                "일괄 삭제에서 테스트 회원 생성 API 로 만든 QA 회원까지 지울지 (기본 false). prefix·hostMemberId 와 무관하게 " +
                    "QA 회원 전원과 그들이 방장인 [QA] 룸을 지운다. 회원 단계가 실패해도 룸 삭제는 이미 반영돼 있다. 목록 조회는 무시한다",
            ),
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
        fieldWithPath("data.deleted.resumes").type(JsonFieldType.NUMBER).description("resume (QA 회원 삭제 시에만 0 이 아니다)"),
        fieldWithPath("data.deleted.profiles").type(JsonFieldType.NUMBER).description("member_profile + 관심 직무·회사 (QA 회원 삭제 시)"),
        fieldWithPath("data.deleted.termsAgreements").type(JsonFieldType.NUMBER).description("terms_agreement (QA 회원 삭제 시)"),
        fieldWithPath("data.deleted.refreshTokens").type(JsonFieldType.NUMBER).description("refresh_token (QA 회원 삭제 시)"),
        fieldWithPath("data.deleted.webPushSubscriptions").type(JsonFieldType.NUMBER).description("web_push_subscription (QA 회원 삭제 시)"),
        fieldWithPath("data.deleted.socialAccounts").type(JsonFieldType.NUMBER).description("social_account (QA 회원 삭제 시)"),
        fieldWithPath("data.deleted.members").type(JsonFieldType.NUMBER).description("member (QA 회원 삭제 시). 그 밖의 API 는 항상 0"),
        fieldWithPath("data.deleted.total").type(JsonFieldType.NUMBER).description("위 건수의 합"),
    )
}

private const val QA_DATA_PATH = "/v1/dev/qa-data"
private const val ROOM_PATH = "/v1/dev/rooms/{roomId}"
private const val RESET_PATH = "/v1/dev/members/{memberId}/reset"
private const val SCHEDULE_PATH = "/v1/dev/rooms/{roomId}/schedule"
private const val MEMBERS_PATH = "/v1/dev/members"
private const val MEMBER_PATH = "/v1/dev/members/{memberId}"
private const val RESUME_SUMMARY_PATH = "/v1/dev/resumes/{resumeId}/summary"
