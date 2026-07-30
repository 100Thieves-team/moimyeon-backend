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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class InterviewControllerTest : RestDocsTest() {
    private val createSummary = "면접 생성"
    private val createDescription =
        "생성 위저드(B·06~B·09)의 입력을 한 번에 받아 면접을 만들고 즉시 모집(RECRUITING) 상태로 등록한다(§4.8). " +
            "회사·직무·지역·이력서는 카탈로그/업로드 참조 id 를 받는다. " +
            "(모킹: 도메인 구현 전까지 고정 식별자(interviewId=1024)를 반환한다)"
    private val formOptionsSummary = "면접 생성 폼 선택지 조회"
    private val formOptionsDescription =
        "면접 정보·진행 방식(B·06·B·07) 폼의 회차·유형·진행 방식·예상 시간·인원 제약 선택지를 한 번에 내려준다. " +
            "라벨을 서버가 소유하도록 목으로 제공한다."
    private val detailSummary = "면접 상세 조회"
    private val detailDescription =
        "생성 완료(B·10) 및 상세 화면. 공개 데이터만 노출한다(§6). 방장 블록의 신뢰 지표는 공개 프로필 API 와 같은 값을 미러링한다. " +
            "(모킹: 도메인 구현 전까지 figma 목업 값 고정 반환)"

    // B·06~B·09 위저드가 모아 보내는 생성 페이로드. 형식 검증만 걸려 있어 유효한 값이면 그대로 통과한다.
    private val createRequestJson =
        """
        {
          "companyId": 1,
          "jobPostingId": 1,
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
          "discloseResumeOriginal": true
        }
        """.trimIndent()

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            InterviewController(),
            controllerAdvice = ApiControllerAdvice(),
            validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() },
        )
    }

    @Test
    fun createInterview() {
        mockMvc.perform(
            post("/v1/interviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "createInterview",
                    createSummary,
                    createDescription,
                    requestFields(
                        fieldWithPath("companyId").type(JsonFieldType.NUMBER).description("회사 id (필수, /v1/companies 검색)"),
                        fieldWithPath("jobPostingId").type(JsonFieldType.NUMBER).optional()
                            .description("채용 공고 id (선택, /v1/companies/{companyId}/job-postings)"),
                        fieldWithPath("jobRoleId").type(JsonFieldType.NUMBER).description("직무 id (필수, /v1/job-roles)"),
                        fieldWithPath("round").type(JsonFieldType.STRING).description("면접 회차 (FIRST | SECOND | THIRD | FINAL)"),
                        fieldWithPath("type").type(JsonFieldType.STRING).optional()
                            .description("면접 유형 (JOB | CULTURE_FIT | EXECUTIVE | TECH_ASSIGNMENT, 선택)"),
                        fieldWithPath("method").type(JsonFieldType.STRING).description("진행 방식 (ONLINE | OFFLINE)"),
                        fieldWithPath("sigunguId").type(JsonFieldType.NUMBER).optional()
                            .description("지역 시군구 id (OFFLINE 일 때, /v1/regions)"),
                        fieldWithPath("minParticipants").type(JsonFieldType.NUMBER).description("최소 인원 (방장 포함, 2 이상)"),
                        fieldWithPath("maxParticipants").type(JsonFieldType.NUMBER).description("최대 인원 (8 이하, 최소 인원 이상)"),
                        fieldWithPath("schedule.date").type(JsonFieldType.STRING).description("면접 날짜 (yyyy-MM-dd)"),
                        fieldWithPath("schedule.startTime").type(JsonFieldType.STRING).description("시작 시각 (HH:mm)"),
                        fieldWithPath("schedule.durationMinutes").type(JsonFieldType.NUMBER).description("예상 소요 시간(분)"),
                        fieldWithPath("title").type(JsonFieldType.STRING).description("면접 제목 (필수, 최대 60자)"),
                        fieldWithPath("description").type(JsonFieldType.STRING).optional().description("면접 소개 (선택, 최대 1000자)"),
                        fieldWithPath("resumeId").type(JsonFieldType.NUMBER).description("업로드한 이력서 id (POST /v1/resumes)"),
                        fieldWithPath("discloseResumeOriginal").type(JsonFieldType.BOOLEAN).description("이력서 원본 공개 여부 (기본 true)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.interviewId").type(JsonFieldType.NUMBER).description("생성된 면접 id (상세로 이동에 사용)"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("면접 상태 (RECRUITING)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun formOptions() {
        mockMvc.perform(get("/v1/interviews/form-options"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "interviewFormOptions",
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
    fun interviewDetail() {
        mockMvc.perform(get("/v1/interviews/{interviewId}", 1024L))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "interviewDetail",
                    detailSummary,
                    detailDescription,
                    pathParameters(
                        parameterWithName("interviewId").description("조회할 면접 id"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.interviewId").type(JsonFieldType.NUMBER).description("면접 id"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("면접 상태 (RECRUITING)"),
                        fieldWithPath("data.statusLabel").type(JsonFieldType.STRING).description("상태 표시명 (모집 중)"),
                        fieldWithPath("data.title").type(JsonFieldType.STRING).description("면접 제목"),
                        fieldWithPath("data.company.companyId").type(JsonFieldType.NUMBER).description("회사 id"),
                        fieldWithPath("data.company.name").type(JsonFieldType.STRING).description("회사명"),
                        fieldWithPath("data.jobPosting.jobPostingId").type(JsonFieldType.NUMBER).optional().description("채용 공고 id (없으면 미노출)"),
                        fieldWithPath("data.jobPosting.title").type(JsonFieldType.STRING).optional().description("채용 공고 제목"),
                        fieldWithPath("data.jobRole.jobRoleId").type(JsonFieldType.NUMBER).description("직무 id"),
                        fieldWithPath("data.jobRole.code").type(JsonFieldType.STRING).description("직무 코드"),
                        fieldWithPath("data.jobRole.displayName").type(JsonFieldType.STRING).description("직무 표시명"),
                        fieldWithPath("data.round").type(JsonFieldType.STRING).description("면접 회차 코드"),
                        fieldWithPath("data.roundLabel").type(JsonFieldType.STRING).description("면접 회차 표시명"),
                        fieldWithPath("data.type").type(JsonFieldType.STRING).optional().description("면접 유형 코드 (선택)"),
                        fieldWithPath("data.typeLabel").type(JsonFieldType.STRING).optional().description("면접 유형 표시명 (선택)"),
                        fieldWithPath("data.method").type(JsonFieldType.STRING).description("진행 방식 코드 (ONLINE | OFFLINE)"),
                        fieldWithPath("data.methodLabel").type(JsonFieldType.STRING).description("진행 방식 표시명"),
                        fieldWithPath("data.region.sigunguId").type(JsonFieldType.NUMBER).optional().description("지역 시군구 id (OFFLINE 일 때)"),
                        fieldWithPath("data.region.label").type(JsonFieldType.STRING).optional().description("지역 표시명 (서울 강남구)"),
                        fieldWithPath("data.schedule.date").type(JsonFieldType.STRING).description("면접 날짜 (yyyy-MM-dd)"),
                        fieldWithPath("data.schedule.dayOfWeekLabel").type(JsonFieldType.STRING).description("요일 표시명 (토)"),
                        fieldWithPath("data.schedule.startTime").type(JsonFieldType.STRING).description("시작 시각 (HH:mm)"),
                        fieldWithPath("data.schedule.startTimeLabel").type(JsonFieldType.STRING).description("시작 시각 표시명 (오후 2:00)"),
                        fieldWithPath("data.schedule.durationMinutes").type(JsonFieldType.NUMBER).description("예상 소요 시간(분)"),
                        fieldWithPath("data.schedule.durationLabel").type(JsonFieldType.STRING).description("소요 시간 표시명 (90분)"),
                        fieldWithPath("data.schedule.displayLabel").type(JsonFieldType.STRING).description("일정 전체 표시 문구"),
                        fieldWithPath("data.description").type(JsonFieldType.STRING).optional().description("면접 소개 (선택)"),
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
                        fieldWithPath("data.host.stats.completedInterviewCount").type(JsonFieldType.NUMBER).description("방장 완료 면접 수"),
                        fieldWithPath("data.host.stats.attendanceRate").type(JsonFieldType.NUMBER).description("방장 출석률 (%)"),
                        fieldWithPath("data.host.stats.averageRating").type(JsonFieldType.NUMBER).description("방장 평균 별점"),
                        fieldWithPath("data.host.aiSummary").type(JsonFieldType.STRING).description("방장 이력 AI 요약"),
                        fieldWithPath("data.viewerRole").type(JsonFieldType.STRING).description("조회자 역할 (HOST | PARTICIPANT | GUEST)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
