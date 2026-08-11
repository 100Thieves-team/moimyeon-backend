package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.CompanyResponse
import io.plady.moimyeon.core.api.controller.v1.response.JobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomJobPostingResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRecruitSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRegionResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomScheduleResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomsResponse
import io.plady.moimyeon.core.api.facade.RoomFacade
import io.plady.moimyeon.core.api.facade.RoomSearchFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.domain.room.RoomDescription
import io.plady.moimyeon.core.domain.room.RoomDetail
import io.plady.moimyeon.core.domain.room.RoomSchedule
import io.plady.moimyeon.core.domain.room.RoomSearchCondition
import io.plady.moimyeon.core.domain.room.RoomService
import io.plady.moimyeon.core.domain.room.RoomSortOrder
import io.plady.moimyeon.core.domain.room.RoomTitle
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class RoomControllerTest : RestDocsTest() {
    private lateinit var roomService: RoomService
    private lateinit var roomSearchFacade: RoomSearchFacade
    private val hostMemberId: UUID = UUID.randomUUID()
    private val principal = Principal { hostMemberId.toString() }
    private val createdRoomId: UUID = UUID.fromString("01920000-0000-7000-8000-000000000001")

    private val createSummary = "룸 생성"
    private val createDescription =
        "생성 위저드(「룸 생성」 §4.1~§4.8)의 입력을 한 번에 받아 룸을 만들고 즉시 모집(RECRUITING) 상태로 등록한다(§4.8). " +
            "공고를 고르면 회사가 함께 확정되므로 룸에는 회사를 따로 저장하지 않는다(공고 → 회사 파생). " +
            "직무·지역·이력서는 카탈로그/보관함 참조 id 를 받는다. " +
            "(모킹: 도메인 구현 전까지 고정 roomId(UUID)를 반환한다)"
    private val formOptionsSummary = "룸 생성 폼 선택지 조회"
    private val formOptionsDescription =
        "기본 정보·진행 방식(§4.1·§4.2) 폼의 회차·유형·진행 방식·예상 시간·인원 제약 선택지를 한 번에 내려준다. " +
            "라벨을 서버가 소유하도록 목으로 제공한다."
    private val listSummary = "룸 탐색 목록 조회"
    private val listDescription =
        "조건에 맞는 모집 중인 룸 목록을 조회한다(「룸 탐색」 §4.1~§4.3). 비로그인도 조회할 수 있다. " +
            "필터는 AND 로 결합하고, 완료·취소·일정 경과 룸은 제외된다. " +
            "커서 페이지네이션이며 nextCursor 가 null 이면 마지막 페이지다. 커서는 불투명 토큰이라 해석하지 말고 그대로 다시 보낸다. " +
            "잘못된 필터·정렬 값은 그 값만 무시하고 나머지 조건으로 조회한다. " +
            "회사·공고·직무·지역 표시명은 참조가 끊어졌을 때(회사 미매칭 공고, 폐기된 직무 등) null 로 내려가고 룸 자체는 목록에 남는다."
    private val detailSummary = "룸 단건 조회"
    private val detailDescription =
        "룸의 실제 저장 데이터 + 현재 인원 + 방장 식별자를 반환한다(§6 공개 데이터). 현재 인원 = 활성 참여 수, " +
            "모집 상태는 정원 충족 여부로 계산한다. 회사·공고·직무 표시명, 방장 프로필/신뢰 지표 enrich 는 별도 이슈. 존재하지 않는 룸은 404(E1405)."

    // 위저드가 모아 보내는 생성 페이로드. 형식 검증만 걸려 있어 유효한 값이면 그대로 통과한다.
    private val createRequestJson =
        """
        {
          "postingId": 1,
          "jobRoleId": 1,
          "round": "FIRST",
          "type": "JOB",
          "method": "OFFLINE",
          "sigunguId": 1,
          "minParticipants": 3,
          "maxParticipants": 6,
          "schedule": {
            "date": "2026-08-01",
            "startTime": "14:00",
            "durationMinutes": 90
          },
          "title": "달빛페이 프론트 1차, 실전처럼 봐요",
          "description": "실제 1차 면접 형식 그대로 진행해요. 결제·정산 도메인 위주로 준비할게요.",
          "resumeId": "01920000-0000-7000-8000-000000000101",
          "resumePublic": true
        }
        """.trimIndent()

    private val cancelSummary = "룸 취소"
    private val cancelDescription =
        "방장이 모집을 접는다(「룸 참여」 §4.9). 룸 상태가 CANCELED 가 되고, 남아 있던 대기 신청은 같은 트랜잭션에서 " +
            "일괄 종료된다. 반려가 아니므로 신청자는 재신청 차단에 걸리지 않는다. " +
            "방장 외 참여자가 남아 있으면 취소할 수 없다(E1420) — 그때는 나가기로 방장을 넘겨야 한다."

    // 확정 조건은 일정과 현재 시각을 비교하므로 문서 예시가 흔들리지 않게 시각을 고정한다.
    private val fixedClock: Clock = Clock.fixed(
        LocalDateTime.of(2026, 8, 1, 12, 0).toInstant(ZoneOffset.UTC),
        ZoneOffset.UTC,
    )

    @BeforeEach
    fun setUp() {
        roomService = mockk()
        roomSearchFacade = mockk()
        mockMvc = mockController(
            RoomController(RoomFacade(roomService, fixedClock), roomSearchFacade),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    // 목록 응답은 Facade 가 조립을 끝내고 오므로, 컨트롤러 문서화는 그 결과를 고정값으로 둔다.
    private fun sampleRoomsResponse(): RoomsResponse = RoomsResponse(
        rooms = listOf(
            RoomSummaryResponse(
                roomId = createdRoomId,
                title = "달빛페이 프론트 1차, 실전처럼 봐요",
                company = CompanyResponse(companyId = 1L, name = "달빛페이"),
                jobPosting = RoomJobPostingResponse(jobPostingId = 1L, postingName = "프론트엔드 개발자 (결제플랫폼)"),
                jobRole = JobRoleResponse(jobRoleId = 1L, code = "FRONTEND_DEVELOPER", displayName = "프론트엔드 개발"),
                round = InterviewStage.FIRST.name,
                roundLabel = InterviewStage.FIRST.label,
                type = InterviewType.JOB.name,
                typeLabel = InterviewType.JOB.label,
                method = "OFFLINE",
                methodLabel = "오프라인",
                region = RoomRegionResponse(sigunguId = 1L, label = "서울 강남구"),
                schedule = RoomScheduleResponse.from(RoomSchedule(LocalDateTime.of(2026, 9, 5, 14, 0), 90)),
                recruit = RoomRecruitSummaryResponse(
                    current = 3,
                    max = 8,
                    pending = 5,
                    recruitStatus = "RECRUITING",
                    recruitStatusLabel = "모집 중",
                ),
            ),
        ),
        sort = RoomSortOrder.SCHEDULE.name,
        totalCount = 1,
        nextCursor = "djE6U0NIRURVTEU6MjAyNi0wOS0wNVQxNDowMDowMTIzNA",
    )

    private fun emptyRoomsResponse(): RoomsResponse = RoomsResponse(rooms = emptyList(), sort = RoomSortOrder.SCHEDULE.name, totalCount = 0, nextCursor = null)

    // 생성 응답은 도메인 Room(id·status)만 쓰므로, 서비스는 목으로 두고 고정 Room 을 돌려준다.
    private fun sampleRoom(): Room = Room.create(
        id = createdRoomId,
        jobPostingId = 1L,
        jobRoleId = 1L,
        title = RoomTitle("달빛페이 프론트 1차, 실전처럼 봐요"),
        description = RoomDescription("실제 1차 면접 형식 그대로 진행해요. 결제·정산 도메인 위주로 준비할게요."),
        interviewStage = InterviewStage.FIRST,
        interviewType = InterviewType.JOB,
        meetingPlace = MeetingPlace.Offline(sigunguId = 1L),
        capacity = RoomCapacity(min = 3, max = 6),
        schedule = RoomSchedule(startAt = LocalDateTime.now().plusDays(1), durationMinutes = 90),
        resumeSharingPolicy = ResumeSharingPolicy.AI_SUMMARY_ONLY,
        now = LocalDateTime.now(),
    )

    // 인원 규칙은 값 객체 RoomCapacity 가 검증한다 → 형식 오류(E400)가 아니라 도메인 코드 E1402(INVALID_ROOM_CAPACITY).
    // create 는 @LoginMember 가 필수라, 검증에 도달하려면 인증 principal 이 있어야 한다.
    @Test
    fun `createRoom 최소 인원이 최대 인원보다 크면 E1402`() {
        mockMvc.perform(
            post("/v1/rooms")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson.replace("\"maxParticipants\": 6", "\"maxParticipants\": 2")),
        )
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1402\"") }
            .andDo(documentApi("createRoom-e1402", createSummary, createDescription, errorResponseFields()))
    }

    @Test
    fun `createRoom 최소 인원이 2보다 작으면 E1402`() {
        mockMvc.perform(
            post("/v1/rooms")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson.replace("\"minParticipants\": 3", "\"minParticipants\": 1")),
        )
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1402\"") }
            .andDo(documentApi("createRoom-e1402-min-participants", createSummary, createDescription, errorResponseFields()))
    }

    @Test
    fun `createRoom 최대 인원이 8보다 크면 E1402`() {
        mockMvc.perform(
            post("/v1/rooms")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson.replace("\"maxParticipants\": 6", "\"maxParticipants\": 9")),
        )
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1402\"") }
            .andDo(documentApi("createRoom-e1402-max-participants", createSummary, createDescription, errorResponseFields()))
    }

    @Test
    fun `cancelRoom 참여자가 없는 모집 중 룸을 취소한다`() {
        every { roomService.cancelRoom(hostMemberId, createdRoomId) } returns Unit

        mockMvc.perform(post("/v1/rooms/{roomId}/cancellation", createdRoomId).principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "cancelRoom",
                    cancelSummary,
                    cancelDescription,
                    pathParameters(parameterWithName("roomId").description("취소할 룸 식별자")),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `cancelRoom 참여자가 남아 있으면 E1420`() {
        every { roomService.cancelRoom(hostMemberId, createdRoomId) } throws
            CoreException(CoreErrorType.ROOM_HAS_PARTICIPANTS)

        mockMvc.perform(post("/v1/rooms/{roomId}/cancellation", createdRoomId).principal(principal))
            .andExpect(status().isConflict)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1420\"") }
            .andDo(documentApi("cancelRoom-e1420", cancelSummary, cancelDescription, errorResponseFields()))
    }

    // 이미 취소된 룸에 다시 요청해도 같은 코드다. 취소는 멱등하게 성공하지 않는다.
    @Test
    fun `cancelRoom 모집 중이 아니면 E1410`() {
        every { roomService.cancelRoom(hostMemberId, createdRoomId) } throws
            CoreException(CoreErrorType.ROOM_NOT_RECRUITING)

        mockMvc.perform(post("/v1/rooms/{roomId}/cancellation", createdRoomId).principal(principal))
            .andExpect(status().isConflict)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E1410\"") }
            .andDo(documentApi("cancelRoom-e1410", cancelSummary, cancelDescription, errorResponseFields()))
    }

    @Test
    fun createRoom() {
        every { roomService.createRoom(any(), any()) } returns sampleRoom()
        mockMvc.perform(
            post("/v1/rooms")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "createRoom",
                    createSummary,
                    createDescription,
                    requestFields(
                        fieldWithPath("postingId").type(JsonFieldType.NUMBER)
                            .description("채용 공고 id (필수, /v1/companies/{companyId}/job-postings 또는 POST /v1/job-postings). 회사는 공고에서 파생"),
                        fieldWithPath("jobRoleId").type(JsonFieldType.NUMBER).description("직무 id (필수, /v1/job-roles)"),
                        fieldWithPath("round").type(JsonFieldType.STRING).description("면접 회차 (FIRST | SECOND | THIRD | ETC)"),
                        fieldWithPath("type").type(JsonFieldType.STRING).optional()
                            .description("면접 유형 (JOB | CULTURE_FIT | EXECUTIVE | TECH_ASSIGNMENT, 선택)"),
                        fieldWithPath("method").type(JsonFieldType.STRING).description("진행 방식 (ONLINE | OFFLINE)"),
                        fieldWithPath("sigunguId").type(JsonFieldType.NUMBER).optional()
                            .description("지역 시군구 id (OFFLINE 일 때, /v1/regions)"),
                        fieldWithPath("minParticipants").type(JsonFieldType.NUMBER).description("최소 인원 (방장 포함, 2 이상)"),
                        fieldWithPath("maxParticipants").type(JsonFieldType.NUMBER).description("최대 인원 (8 이하, 최소 인원 이상)"),
                        fieldWithPath("schedule.date").type(JsonFieldType.STRING).description("진행 날짜 (yyyy-MM-dd, 미래)"),
                        fieldWithPath("schedule.startTime").type(JsonFieldType.STRING).description("시작 시각 (HH:mm)"),
                        fieldWithPath("schedule.durationMinutes").type(JsonFieldType.NUMBER).description("예상 소요 시간(분)"),
                        fieldWithPath("title").type(JsonFieldType.STRING).description("룸 제목 (필수, 최대 60자)"),
                        fieldWithPath("description").type(JsonFieldType.STRING).optional().description("룸 설명 (선택, 최대 1000자)"),
                        fieldWithPath("resumeId").type(JsonFieldType.STRING)
                            .description("방장이 제출할 보관 이력서 id (UUID, /v1/members/me/resumes)"),
                        fieldWithPath("resumePublic").type(JsonFieldType.BOOLEAN).description("이력서 원본 공개 여부 (룸 속성, 기본 false)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.roomId").type(JsonFieldType.STRING).description("생성된 룸 id (UUID, 상세로 이동에 사용)"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("룸 상태 (RECRUITING)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun formOptions() {
        mockMvc.perform(get("/v1/rooms/form-options"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "roomFormOptions",
                    formOptionsSummary,
                    formOptionsDescription,
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.rounds").type(JsonFieldType.ARRAY).description("면접 회차 선택지"),
                        fieldWithPath("data.rounds[].code").type(JsonFieldType.STRING).description("회차 코드"),
                        fieldWithPath("data.rounds[].label").type(JsonFieldType.STRING).description("회차 표시명"),
                        fieldWithPath("data.types").type(JsonFieldType.ARRAY).description("면접 유형 선택지"),
                        fieldWithPath("data.types[].code").type(JsonFieldType.STRING).description("유형 코드"),
                        fieldWithPath("data.types[].label").type(JsonFieldType.STRING).description("유형 표시명"),
                        fieldWithPath("data.methods").type(JsonFieldType.ARRAY).description("진행 방식 선택지"),
                        fieldWithPath("data.methods[].code").type(JsonFieldType.STRING).description("진행 방식 코드 (ONLINE | OFFLINE)"),
                        fieldWithPath("data.methods[].label").type(JsonFieldType.STRING).description("진행 방식 표시명"),
                        fieldWithPath("data.methods[].hint").type(JsonFieldType.STRING).description("선택 시 안내 문구"),
                        fieldWithPath("data.durations").type(JsonFieldType.ARRAY).description("예상 소요 시간 선택지"),
                        fieldWithPath("data.durations[].minutes").type(JsonFieldType.NUMBER).description("소요 시간(분)"),
                        fieldWithPath("data.durations[].label").type(JsonFieldType.STRING).description("소요 시간 표시명"),
                        fieldWithPath("data.participantConstraints.min").type(JsonFieldType.NUMBER).description("허용 최소 인원"),
                        fieldWithPath("data.participantConstraints.max").type(JsonFieldType.NUMBER).description("허용 최대 인원"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun rooms() {
        every { roomSearchFacade.search(any(), any(), any(), any()) } returns sampleRoomsResponse()

        mockMvc.perform(
            get("/v1/rooms")
                .param("method", "OFFLINE")
                .param("availableOnly", "true")
                .param("sort", "SCHEDULE")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "rooms",
                    listSummary,
                    listDescription,
                    queryParameters(
                        parameterWithName("companyId").optional().description("회사 id 필터 (선택). 그 회사의 공고로 만든 룸만 남는다"),
                        parameterWithName("jobPostingId").optional().description("채용 공고 id 필터 (선택). 회사와 함께 주면 둘을 모두 만족하는 공고만 남는다"),
                        parameterWithName("jobRoleId").optional().description("직무 id 필터 (선택)"),
                        parameterWithName("round").optional().description("면접 단계 필터 (FIRST | SECOND | THIRD | ETC, 선택). 지원하지 않는 값은 이 조건만 무시한다"),
                        parameterWithName("method").optional().description("진행 방식 필터 (ONLINE | OFFLINE, 선택). 지원하지 않는 값은 이 조건만 무시한다"),
                        parameterWithName("sigunguId").optional().description("오프라인 지역 시군구 id 필터 (선택)"),
                        parameterWithName("startFrom").optional().description("조회 시작 일시 (ISO-8601, 선택). 시간까지 포함한 한 구간이다"),
                        parameterWithName("startTo").optional().description("조회 종료 일시 (ISO-8601, 선택). 시작보다 앞서면 400 이다"),
                        parameterWithName("availableOnly").optional().description("참여 가능한 룸만 보기 토글 (기본 false)"),
                        parameterWithName("sort").optional().description("정렬 (SCHEDULE 일정 빠른 순 | RECENT 최근 생성순, 기본 SCHEDULE). 지원하지 않는 값은 기본 정렬로 처리한다"),
                        parameterWithName("cursor").optional().description("이전 응답의 nextCursor (선택). 정렬을 바꾸면 버리고 처음부터 조회한다"),
                        parameterWithName("size").optional().description("페이지 크기 (1~50, 기본 20). 범위 밖이면 기본값으로 조회한다"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.rooms").type(JsonFieldType.ARRAY).description("룸 목록"),
                        fieldWithPath("data.rooms[].roomId").type(JsonFieldType.STRING).description("룸 id (UUID)"),
                        fieldWithPath("data.rooms[].title").type(JsonFieldType.STRING).description("룸 제목"),
                        fieldWithPath("data.rooms[].company").type(JsonFieldType.OBJECT).optional().description("회사 (공고에서 파생. 회사를 알 수 없으면 null)"),
                        fieldWithPath("data.rooms[].company.companyId").type(JsonFieldType.NUMBER).optional().description("회사 id"),
                        fieldWithPath("data.rooms[].company.name").type(JsonFieldType.STRING).optional().description("회사명"),
                        fieldWithPath("data.rooms[].jobPosting").type(JsonFieldType.OBJECT).optional().description("채용 공고 (폐기됐으면 null)"),
                        fieldWithPath("data.rooms[].jobPosting.jobPostingId").type(JsonFieldType.NUMBER).optional().description("채용 공고 id"),
                        fieldWithPath("data.rooms[].jobPosting.postingName").type(JsonFieldType.STRING).optional().description("채용 공고명"),
                        fieldWithPath("data.rooms[].jobRole").type(JsonFieldType.OBJECT).optional().description("직무 (폐기됐으면 null)"),
                        fieldWithPath("data.rooms[].jobRole.jobRoleId").type(JsonFieldType.NUMBER).optional().description("직무 id"),
                        fieldWithPath("data.rooms[].jobRole.code").type(JsonFieldType.STRING).optional().description("직무 코드"),
                        fieldWithPath("data.rooms[].jobRole.displayName").type(JsonFieldType.STRING).optional().description("직무 표시명"),
                        fieldWithPath("data.rooms[].round").type(JsonFieldType.STRING).description("면접 단계 코드 (FIRST | SECOND | THIRD | ETC)"),
                        fieldWithPath("data.rooms[].roundLabel").type(JsonFieldType.STRING).description("면접 단계 표시명"),
                        fieldWithPath("data.rooms[].type").type(JsonFieldType.STRING).optional().description("면접 유형 코드 (선택)"),
                        fieldWithPath("data.rooms[].typeLabel").type(JsonFieldType.STRING).optional().description("면접 유형 표시명 (선택)"),
                        fieldWithPath("data.rooms[].method").type(JsonFieldType.STRING).description("진행 방식 코드 (ONLINE | OFFLINE)"),
                        fieldWithPath("data.rooms[].methodLabel").type(JsonFieldType.STRING).description("진행 방식 표시명"),
                        fieldWithPath("data.rooms[].region").type(JsonFieldType.OBJECT).optional().description("오프라인 지역 (온라인이거나 폐기된 지역이면 null)"),
                        fieldWithPath("data.rooms[].region.sigunguId").type(JsonFieldType.NUMBER).optional().description("지역 시군구 id"),
                        fieldWithPath("data.rooms[].region.label").type(JsonFieldType.STRING).optional().description("지역 표시명"),
                        fieldWithPath("data.rooms[].schedule.date").type(JsonFieldType.STRING).description("진행 날짜 (yyyy-MM-dd). 요일 등 표시 문구는 화면이 만든다"),
                        fieldWithPath("data.rooms[].schedule.startTime").type(JsonFieldType.STRING).description("시작 시각 (HH:mm)"),
                        fieldWithPath("data.rooms[].schedule.durationMinutes").type(JsonFieldType.NUMBER).description("예상 소요 시간(분)"),
                        fieldWithPath("data.rooms[].recruit.current").type(JsonFieldType.NUMBER).description("현재 인원 (참여 중인 사람만. 나간 사람은 빠진다)"),
                        fieldWithPath("data.rooms[].recruit.max").type(JsonFieldType.NUMBER).description("최대 인원"),
                        fieldWithPath("data.rooms[].recruit.pending").type(JsonFieldType.NUMBER).description("대기 중인 참가 신청 수"),
                        fieldWithPath("data.rooms[].recruit.recruitStatus").type(JsonFieldType.STRING).description("모집 상태 (RECRUITING | CLOSED, 정원 충족 시 CLOSED)"),
                        fieldWithPath("data.rooms[].recruit.recruitStatusLabel").type(JsonFieldType.STRING).description("모집 상태 표시명"),
                        fieldWithPath("data.sort").type(JsonFieldType.STRING).description("실제로 적용된 정렬"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("조건에 맞는 전체 룸 수 (페이지 크기와 무관)"),
                        fieldWithPath("data.nextCursor").type(JsonFieldType.STRING).optional().description("다음 페이지 커서. 마지막 페이지면 null"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `지원하지 않는 정렬 값을 보내면 기본 정렬로 조회한다`() {
        val sort = slot<RoomSortOrder>()
        every { roomSearchFacade.search(any(), capture(sort), any(), any()) } returns sampleRoomsResponse()

        mockMvc.perform(get("/v1/rooms").param("sort", "POPULAR"))
            .andExpect(status().isOk)

        assertThat(sort.captured).isEqualTo(RoomSortOrder.SCHEDULE)
    }

    @Test
    fun `잘못된 면접 단계 값은 그 조건만 무시하고 조회한다`() {
        val condition = slot<RoomSearchCondition>()
        every { roomSearchFacade.search(capture(condition), any(), any(), any()) } returns sampleRoomsResponse()

        mockMvc.perform(get("/v1/rooms").param("round", "FOURTH").param("jobRoleId", "2"))
            .andExpect(status().isOk)

        assertThat(condition.captured.interviewStage).isNull()
        assertThat(condition.captured.jobRoleId).isEqualTo(2L)
    }

    @Test
    fun `페이지 크기가 허용 범위를 벗어나면 기본 크기로 조회한다`() {
        val size = slot<Int>()
        every { roomSearchFacade.search(any(), any(), any(), capture(size)) } returns sampleRoomsResponse()

        mockMvc.perform(get("/v1/rooms").param("size", "1000"))
            .andExpect(status().isOk)

        assertThat(size.captured).isEqualTo(20)
    }

    @Test
    fun `존재하지 않는 직무로 좁히면 조건을 무시하지 않고 그대로 조회한다`() {
        val condition = slot<RoomSearchCondition>()
        every { roomSearchFacade.search(capture(condition), any(), any(), any()) } returns emptyRoomsResponse()

        mockMvc.perform(get("/v1/rooms").param("jobRoleId", "99999"))
            .andExpect(status().isOk)

        assertThat(condition.captured.jobRoleId).isEqualTo(99999L)
    }

    @Test
    fun `깨진 커서를 보내면 E400 을 반환한다`() {
        mockMvc.perform(get("/v1/rooms").param("cursor", "broken-token"))
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
            .andDo(documentApi("rooms-e400-cursor", listSummary, listDescription, errorResponseFields()))
    }

    @Test
    fun `조회 범위의 시작이 끝보다 늦으면 E400 을 반환한다`() {
        mockMvc.perform(
            get("/v1/rooms")
                .param("startFrom", "2026-08-20T19:00:00")
                .param("startTo", "2026-08-10T19:00:00"),
        )
            .andExpect(status().isBadRequest)
            .andExpect { assertThat(it.response.contentAsString).contains("\"code\":\"E400\"") }
    }

    @Test
    fun roomDetail() {
        every { roomService.getRoom(any()) } returns RoomDetail(
            room = sampleRoom(),
            hostMemberId = hostMemberId,
            currentParticipants = 1,
            pendingApplicationCount = 5,
        )
        mockMvc.perform(get("/v1/rooms/{roomId}", createdRoomId.toString()))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "roomDetail",
                    detailSummary,
                    detailDescription,
                    pathParameters(
                        parameterWithName("roomId").description("조회할 룸 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.roomId").type(JsonFieldType.STRING).description("룸 id (UUID)"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("룸 상태 (RECRUITING | CONFIRMED | COMPLETED | CANCELED)"),
                        fieldWithPath("data.jobPostingId").type(JsonFieldType.NUMBER).description("채용 공고 id (회사는 공고에서 파생)"),
                        fieldWithPath("data.jobRoleId").type(JsonFieldType.NUMBER).description("직무 id"),
                        fieldWithPath("data.title").type(JsonFieldType.STRING).description("룸 제목"),
                        fieldWithPath("data.description").type(JsonFieldType.STRING).optional().description("룸 설명 (선택)"),
                        fieldWithPath("data.round").type(JsonFieldType.STRING).description("면접 회차 (FIRST | SECOND | THIRD | ETC)"),
                        fieldWithPath("data.roundLabel").type(JsonFieldType.STRING).description("면접 회차 표시명"),
                        fieldWithPath("data.type").type(JsonFieldType.STRING).optional().description("면접 유형 (선택)"),
                        fieldWithPath("data.typeLabel").type(JsonFieldType.STRING).optional().description("면접 유형 표시명 (선택)"),
                        fieldWithPath("data.method").type(JsonFieldType.STRING).description("진행 방식 (ONLINE | OFFLINE)"),
                        fieldWithPath("data.sigunguId").type(JsonFieldType.NUMBER).optional().description("오프라인 지역 시군구 id (온라인이면 null)"),
                        fieldWithPath("data.schedule.startAt").type(JsonFieldType.STRING).description("진행 시작 일시 (ISO-8601)"),
                        fieldWithPath("data.schedule.durationMinutes").type(JsonFieldType.NUMBER).description("예상 소요 시간(분)"),
                        fieldWithPath("data.recruit.current").type(JsonFieldType.NUMBER).description("현재 인원 (활성 참여 수, 방장 포함)"),
                        fieldWithPath("data.recruit.min").type(JsonFieldType.NUMBER).description("최소 인원"),
                        fieldWithPath("data.recruit.max").type(JsonFieldType.NUMBER).description("최대 인원"),
                        fieldWithPath("data.recruit.recruitStatus").type(JsonFieldType.STRING).description("모집 상태 (RECRUITING | CLOSED, 정원 충족 시 CLOSED)"),
                        fieldWithPath("data.recruit.pendingApplicationCount").type(JsonFieldType.NUMBER)
                            .description("대기 중인 참가 신청 수. 수만 공개하고 대기자 목록은 방장 외 비공개다"),
                        fieldWithPath("data.confirmation").type(JsonFieldType.OBJECT)
                            .description("진행 확정 준비 여부. 이 룸의 사실이며 조회자가 확정할 수 있는지와는 다르다"),
                        fieldWithPath("data.confirmation.ready").type(JsonFieldType.BOOLEAN)
                            .description("확정 가능 여부 (모집 중 && 일정 미경과 && 인원 >= 최소 인원)"),
                        fieldWithPath("data.confirmation.blockReason").type(JsonFieldType.OBJECT).optional()
                            .description("확정할 수 없는 사유. ready 가 true 면 null"),
                        fieldWithPath("data.confirmation.blockReason.code").type(JsonFieldType.STRING).optional()
                            .description("사유 코드 (ROOM_CONFIRMED | ROOM_IN_PROGRESS | ROOM_COMPLETED | ROOM_CANCELED | SCHEDULE_PASSED | BELOW_MIN_CAPACITY)"),
                        fieldWithPath("data.confirmation.blockReason.label").type(JsonFieldType.STRING).optional()
                            .description("화면에 그대로 쓰는 사유 문구. 인원 미달이면 현재 인원과 최소 인원이 들어간다"),
                        fieldWithPath("data.resumePublic").type(JsonFieldType.BOOLEAN).description("이력서 원본 공개 여부 (룸 속성)"),
                        fieldWithPath("data.hostMemberId").type(JsonFieldType.STRING).description("방장 회원 식별자 (UUID)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
