package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.test.api.RestDocsTest
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

class RoomControllerTest : RestDocsTest() {
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
            "(모킹: 필터·정렬을 실제 적용하지 않고 고정 목록을 반환하되 요청 sort 는 그대로 되돌려준다)"
    private val detailSummary = "룸 상세 조회"
    private val detailDescription =
        "생성 완료 및 탐색 상세 화면(「룸 탐색」 §4.4). 공개 데이터만 노출한다(§6). 방장 블록의 신뢰 지표는 공개 프로필 API 와 같은 값을 미러링한다. " +
            "(모킹: 도메인 구현 전까지 figma 목업 값 고정 반환)"

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
          "resumeId": 90001,
          "resumePublic": true
        }
        """.trimIndent()

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            RoomController(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun createRoom() {
        mockMvc.perform(
            post("/v1/rooms")
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
                        fieldWithPath("round").type(JsonFieldType.STRING).description("면접 회차 (FIRST | SECOND | THIRD | FINAL)"),
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
                        fieldWithPath("resumeId").type(JsonFieldType.NUMBER).description("방장이 제출할 보관 이력서 id (회원 보관함)"),
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
        mockMvc.perform(
            get("/v1/rooms")
                .param("method", "OFFLINE")
                .param("availableOnly", "true")
                .param("sort", "SCHEDULE"),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "rooms",
                    listSummary,
                    listDescription,
                    queryParameters(
                        parameterWithName("companyId").optional().description("회사 id 필터 (선택)"),
                        parameterWithName("jobRoleId").optional().description("직무 id 필터 (선택)"),
                        parameterWithName("round").optional().description("면접 차수 필터 (선택)"),
                        parameterWithName("method").optional().description("진행 방식 필터 (ONLINE | OFFLINE, 선택)"),
                        parameterWithName("sigunguId").optional().description("오프라인 지역 시군구 id 필터 (선택)"),
                        parameterWithName("availableOnly").optional().description("참여 가능한 룸만 보기 토글 (기본 false)"),
                        parameterWithName("sort").optional().description("정렬 (SCHEDULE 일정 빠른 순 | RECENT 최근 생성순 | CLOSING 마감 임박순, 기본 SCHEDULE)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.rooms").type(JsonFieldType.ARRAY).description("룸 목록"),
                        fieldWithPath("data.rooms[].roomId").type(JsonFieldType.STRING).description("룸 id (UUID)"),
                        fieldWithPath("data.rooms[].title").type(JsonFieldType.STRING).description("룸 제목"),
                        fieldWithPath("data.rooms[].company.companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.rooms[].company.name").type(JsonFieldType.STRING).description("회사명"),
                        fieldWithPath("data.rooms[].jobPosting").type(JsonFieldType.OBJECT).optional().description("채용 공고 (없으면 null)"),
                        fieldWithPath("data.rooms[].jobPosting.jobPostingId").type(JsonFieldType.NUMBER).optional().description("채용 공고 id"),
                        fieldWithPath("data.rooms[].jobPosting.postingName").type(JsonFieldType.STRING).optional().description("채용 공고명"),
                        fieldWithPath("data.rooms[].jobRole.jobRoleId").type(JsonFieldType.NUMBER).description("직무 id"),
                        fieldWithPath("data.rooms[].jobRole.code").type(JsonFieldType.STRING).description("직무 코드"),
                        fieldWithPath("data.rooms[].jobRole.displayName").type(JsonFieldType.STRING).description("직무 표시명"),
                        fieldWithPath("data.rooms[].round").type(JsonFieldType.STRING).description("면접 회차 코드"),
                        fieldWithPath("data.rooms[].roundLabel").type(JsonFieldType.STRING).description("면접 회차 표시명"),
                        fieldWithPath("data.rooms[].type").type(JsonFieldType.STRING).optional().description("면접 유형 코드 (선택)"),
                        fieldWithPath("data.rooms[].typeLabel").type(JsonFieldType.STRING).optional().description("면접 유형 표시명 (선택)"),
                        fieldWithPath("data.rooms[].method").type(JsonFieldType.STRING).description("진행 방식 코드 (ONLINE | OFFLINE)"),
                        fieldWithPath("data.rooms[].methodLabel").type(JsonFieldType.STRING).description("진행 방식 표시명"),
                        fieldWithPath("data.rooms[].region").type(JsonFieldType.OBJECT).optional().description("오프라인 지역 (온라인이면 null)"),
                        fieldWithPath("data.rooms[].region.sigunguId").type(JsonFieldType.NUMBER).optional().description("지역 시군구 id"),
                        fieldWithPath("data.rooms[].region.label").type(JsonFieldType.STRING).optional().description("지역 표시명"),
                        fieldWithPath("data.rooms[].schedule.date").type(JsonFieldType.STRING).description("진행 날짜 (yyyy-MM-dd)"),
                        fieldWithPath("data.rooms[].schedule.dayOfWeekLabel").type(JsonFieldType.STRING).description("요일 표시명"),
                        fieldWithPath("data.rooms[].schedule.startTime").type(JsonFieldType.STRING).description("시작 시각 (HH:mm)"),
                        fieldWithPath("data.rooms[].schedule.startTimeLabel").type(JsonFieldType.STRING).description("시작 시각 표시명"),
                        fieldWithPath("data.rooms[].schedule.durationMinutes").type(JsonFieldType.NUMBER).description("예상 소요 시간(분)"),
                        fieldWithPath("data.rooms[].schedule.durationLabel").type(JsonFieldType.STRING).description("소요 시간 표시명"),
                        fieldWithPath("data.rooms[].schedule.displayLabel").type(JsonFieldType.STRING).description("일정 전체 표시 문구"),
                        fieldWithPath("data.rooms[].recruit.current").type(JsonFieldType.NUMBER).description("현재 인원"),
                        fieldWithPath("data.rooms[].recruit.max").type(JsonFieldType.NUMBER).description("최대 인원"),
                        fieldWithPath("data.rooms[].recruit.recruitStatus").type(JsonFieldType.STRING).description("모집 상태 (RECRUITING | CLOSED, 정원 충족 시 CLOSED)"),
                        fieldWithPath("data.rooms[].recruit.recruitStatusLabel").type(JsonFieldType.STRING).description("모집 상태 표시명"),
                        fieldWithPath("data.sort").type(JsonFieldType.STRING).description("적용된 정렬 (요청 값 에코)"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("전체 결과 수"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun roomDetail() {
        mockMvc.perform(get("/v1/rooms/{roomId}", "01920000-0000-7000-8000-000000000001"))
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
                        fieldWithPath("data.statusLabel").type(JsonFieldType.STRING).description("상태 표시명 (모집 중)"),
                        fieldWithPath("data.title").type(JsonFieldType.STRING).description("룸 제목"),
                        fieldWithPath("data.company.companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.company.name").type(JsonFieldType.STRING).description("회사명"),
                        fieldWithPath("data.jobPosting").type(JsonFieldType.OBJECT).optional().description("채용 공고 (없으면 null)"),
                        fieldWithPath("data.jobPosting.jobPostingId").type(JsonFieldType.NUMBER).optional().description("채용 공고 id"),
                        fieldWithPath("data.jobPosting.postingName").type(JsonFieldType.STRING).optional().description("채용 공고명"),
                        fieldWithPath("data.jobRole.jobRoleId").type(JsonFieldType.NUMBER).description("직무 id"),
                        fieldWithPath("data.jobRole.code").type(JsonFieldType.STRING).description("직무 코드"),
                        fieldWithPath("data.jobRole.displayName").type(JsonFieldType.STRING).description("직무 표시명"),
                        fieldWithPath("data.round").type(JsonFieldType.STRING).description("면접 회차 코드"),
                        fieldWithPath("data.roundLabel").type(JsonFieldType.STRING).description("면접 회차 표시명"),
                        fieldWithPath("data.type").type(JsonFieldType.STRING).optional().description("면접 유형 코드 (선택)"),
                        fieldWithPath("data.typeLabel").type(JsonFieldType.STRING).optional().description("면접 유형 표시명 (선택)"),
                        fieldWithPath("data.method").type(JsonFieldType.STRING).description("진행 방식 코드 (ONLINE | OFFLINE)"),
                        fieldWithPath("data.methodLabel").type(JsonFieldType.STRING).description("진행 방식 표시명"),
                        fieldWithPath("data.region").type(JsonFieldType.OBJECT).optional().description("오프라인 지역 (온라인이면 null)"),
                        fieldWithPath("data.region.sigunguId").type(JsonFieldType.NUMBER).optional().description("지역 시군구 id (OFFLINE 일 때)"),
                        fieldWithPath("data.region.label").type(JsonFieldType.STRING).optional().description("지역 표시명 (서울 강남구)"),
                        fieldWithPath("data.schedule.date").type(JsonFieldType.STRING).description("진행 날짜 (yyyy-MM-dd)"),
                        fieldWithPath("data.schedule.dayOfWeekLabel").type(JsonFieldType.STRING).description("요일 표시명 (토)"),
                        fieldWithPath("data.schedule.startTime").type(JsonFieldType.STRING).description("시작 시각 (HH:mm)"),
                        fieldWithPath("data.schedule.startTimeLabel").type(JsonFieldType.STRING).description("시작 시각 표시명 (오후 2:00)"),
                        fieldWithPath("data.schedule.durationMinutes").type(JsonFieldType.NUMBER).description("예상 소요 시간(분)"),
                        fieldWithPath("data.schedule.durationLabel").type(JsonFieldType.STRING).description("소요 시간 표시명 (90분)"),
                        fieldWithPath("data.schedule.displayLabel").type(JsonFieldType.STRING).description("일정 전체 표시 문구"),
                        fieldWithPath("data.description").type(JsonFieldType.STRING).optional().description("룸 설명 (선택)"),
                        fieldWithPath("data.resumePublic").type(JsonFieldType.BOOLEAN).description("이력서 원본 공개 여부 (룸 속성)"),
                        fieldWithPath("data.resumePolicyLabel").type(JsonFieldType.STRING).description("이력서 공유 정책 표시명"),
                        fieldWithPath("data.notice").type(JsonFieldType.STRING).description("신청 전 안내 문구"),
                        fieldWithPath("data.recruit.current").type(JsonFieldType.NUMBER).description("현재 인원 (방장 겸 참여자 포함)"),
                        fieldWithPath("data.recruit.min").type(JsonFieldType.NUMBER).description("최소 인원"),
                        fieldWithPath("data.recruit.max").type(JsonFieldType.NUMBER).description("최대 인원"),
                        fieldWithPath("data.recruit.confirmable").type(JsonFieldType.BOOLEAN).description("확정 가능 여부 (최소 인원 충족)"),
                        fieldWithPath("data.recruit.remainingToConfirm").type(JsonFieldType.NUMBER).description("확정까지 남은 인원"),
                        fieldWithPath("data.recruit.progressRatio").type(JsonFieldType.NUMBER).description("모집 진행률 (0.0~1.0)"),
                        fieldWithPath("data.recruit.message").type(JsonFieldType.STRING).description("모집 상태 안내 문구"),
                        fieldWithPath("data.host.memberId").type(JsonFieldType.STRING).description("방장 회원 식별자 (UUID)"),
                        fieldWithPath("data.host.nickname").type(JsonFieldType.STRING).description("방장 닉네임"),
                        fieldWithPath("data.host.jobTitle").type(JsonFieldType.STRING).optional().description("방장 직무 (선택)"),
                        fieldWithPath("data.host.isHost").type(JsonFieldType.BOOLEAN).description("조회자가 방장인지 여부"),
                        fieldWithPath("data.host.stats.completedRoomCount").type(JsonFieldType.NUMBER).description("방장 완료 룸 수"),
                        fieldWithPath("data.host.stats.attendanceRate").type(JsonFieldType.NUMBER).description("방장 출석률 (%)"),
                        fieldWithPath("data.host.stats.averageRating").type(JsonFieldType.NUMBER).description("방장 평균 별점"),
                        fieldWithPath("data.host.aiSummary").type(JsonFieldType.STRING).description("방장 이력 AI 요약"),
                        fieldWithPath("data.viewerRole").type(JsonFieldType.STRING).description("조회자 역할 (HOST | PARTICIPANT | APPLICANT | GUEST)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
