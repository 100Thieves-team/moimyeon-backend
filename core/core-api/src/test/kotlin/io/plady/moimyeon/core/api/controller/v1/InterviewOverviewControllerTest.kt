package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.CompletedRoomResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewOverviewResponse
import io.plady.moimyeon.core.api.controller.v1.response.InterviewRoomResponse
import io.plady.moimyeon.core.api.controller.v1.response.ParticipatingRoomResponse
import io.plady.moimyeon.core.api.controller.v1.response.PendingRoomApplicationResponse
import io.plady.moimyeon.core.api.facade.InterviewOverviewFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID

class InterviewOverviewControllerTest : RestDocsTest() {
    private lateinit var facade: InterviewOverviewFacade
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000436")
    private val principal = Principal { memberId.toString() }

    private val summary = "내 면접 목록 조회"
    private val description =
        "인증 회원을 조건으로 처리 대기 신청, 현재 참여 중인 룸, 완료된 룸을 구분해 반환한다(「룸 탐색」 §4.8). " +
            "신청 중은 최근 신청 순, 참여 중은 가까운 일정 순, 완료는 최근 일정 순이다. " +
            "완료 룸의 reviewStatus는 WRITABLE | WRITTEN | NOT_ELIGIBLE_ABSENT | " +
            "NOT_ELIGIBLE_NO_TARGET 값이다."

    @BeforeEach
    fun setUp() {
        facade = mockk()
        mockMvc = mockController(
            InterviewOverviewController(facade),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `인증 회원의 신청과 참여 룸을 구분해 조회한다`() {
        every { facade.getOverview(memberId) } returns sampleOverview()

        mockMvc.perform(get("/v1/members/me/rooms").principal(principal))
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"resumeOriginalName\":\"backend.pdf\"")
                    .contains("\"roomStatus\":\"CONFIRMED\"")
                    .contains("\"reviewStatus\":\"WRITABLE\"")
            }
            .andDo(
                documentApi(
                    "getInterviewOverview",
                    summary,
                    description,
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.pendingApplications").type(JsonFieldType.ARRAY).description("처리 대기 중인 참가 신청 (최근 신청 순)"),
                        fieldWithPath("data.pendingApplications[].applicationId").type(JsonFieldType.NUMBER).description("참가 신청 식별자"),
                        fieldWithPath("data.pendingApplications[].resumeOriginalName").type(JsonFieldType.STRING).description("신청에 제출한 이력서 원본 파일명"),
                        fieldWithPath("data.pendingApplications[].appliedAt").type(JsonFieldType.STRING).description("신청 시각"),
                        fieldWithPath("data.pendingApplications[].room").type(JsonFieldType.OBJECT).description("신청 대상 룸 요약"),
                        *roomFields("data.pendingApplications[].room"),
                        fieldWithPath("data.participatingRooms").type(JsonFieldType.ARRAY).description("현재 참여 중인 룸 (가까운 일정 순)"),
                        fieldWithPath("data.participatingRooms[].room").type(JsonFieldType.OBJECT).description("참여 중인 룸 요약"),
                        *roomFields("data.participatingRooms[].room"),
                        fieldWithPath("data.completedRooms").type(JsonFieldType.ARRAY).description("완료된 룸 (최근 일정 순)"),
                        fieldWithPath("data.completedRooms[].room").type(JsonFieldType.OBJECT).description("완료된 룸 요약"),
                        *roomFields("data.completedRooms[].room"),
                        fieldWithPath("data.completedRooms[].reviewStatus").type(JsonFieldType.STRING)
                            .description(
                                "후기 상태 (WRITABLE | WRITTEN | NOT_ELIGIBLE_ABSENT | " +
                                    "NOT_ELIGIBLE_NO_TARGET)",
                            ),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `신청과 참여 이력이 없으면 각 구분을 빈 배열로 반환한다`() {
        every { facade.getOverview(memberId) } returns InterviewOverviewResponse(emptyList(), emptyList(), emptyList())

        mockMvc.perform(get("/v1/members/me/rooms").principal(principal))
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"pendingApplications\":[]")
                    .contains("\"participatingRooms\":[]")
                    .contains("\"completedRooms\":[]")
            }
    }

    @Test
    fun `인증 정보 없이 목록을 조회하면 E1102`() {
        mockMvc.perform(get("/v1/members/me/rooms"))
            .andExpect(status().isUnauthorized)
            .andDo(documentApi("getInterviewOverview-e1102", summary, description, errorResponseFields()))
    }

    private fun sampleOverview(): InterviewOverviewResponse {
        return InterviewOverviewResponse(
            pendingApplications = listOf(
                PendingRoomApplicationResponse(
                    applicationId = 1L,
                    resumeOriginalName = "backend.pdf",
                    appliedAt = LocalDateTime.of(2026, 8, 5, 14, 30),
                    room = room("RECRUITING", "01920000-0000-7000-8000-000000000436"),
                ),
            ),
            participatingRooms = listOf(
                ParticipatingRoomResponse(room("CONFIRMED", "01920000-0000-7000-8000-000000000437")),
            ),
            completedRooms = listOf(
                CompletedRoomResponse(
                    room = room("COMPLETED", "01920000-0000-7000-8000-000000000438"),
                    reviewStatus = "WRITABLE",
                ),
            ),
        )
    }

    private fun room(status: String, roomId: String): InterviewRoomResponse {
        return InterviewRoomResponse(
            roomId = UUID.fromString(roomId),
            title = "달빛페이 백엔드 1차 모의면접",
            jobPostingId = 1L,
            jobRoleId = 2L,
            interviewStage = "FIRST",
            interviewType = "JOB",
            meetingType = "OFFLINE",
            sigunguId = 1L,
            startAt = LocalDateTime.of(2026, 8, 20, 19, 0),
            durationMinutes = 60,
            participantCount = 4,
            maxParticipants = 5,
            roomStatus = status,
        )
    }

    private fun roomFields(prefix: String) = arrayOf(
        fieldWithPath("$prefix.roomId").type(JsonFieldType.STRING).description("룸 식별자 (UUID)"),
        fieldWithPath("$prefix.title").type(JsonFieldType.STRING).description("룸 제목"),
        fieldWithPath("$prefix.jobPostingId").type(JsonFieldType.NUMBER).description("채용 공고 식별자"),
        fieldWithPath("$prefix.jobRoleId").type(JsonFieldType.NUMBER).description("직무 식별자"),
        fieldWithPath("$prefix.interviewStage").type(JsonFieldType.STRING).description("면접 단계"),
        fieldWithPath("$prefix.interviewType").type(JsonFieldType.STRING).optional().description("면접 유형"),
        fieldWithPath("$prefix.meetingType").type(JsonFieldType.STRING).description("진행 방식 (ONLINE | OFFLINE)"),
        fieldWithPath("$prefix.sigunguId").type(JsonFieldType.NUMBER).optional().description("오프라인 시군구 식별자"),
        fieldWithPath("$prefix.startAt").type(JsonFieldType.STRING).description("시작 예정 시각"),
        fieldWithPath("$prefix.durationMinutes").type(JsonFieldType.NUMBER).description("진행 시간(분)"),
        fieldWithPath("$prefix.participantCount").type(JsonFieldType.NUMBER)
            .description("표시 참여 인원 (신청·참여 중은 현재 인원, 완료는 실제 출석 인원)"),
        fieldWithPath("$prefix.maxParticipants").type(JsonFieldType.NUMBER).description("최대 참여 인원"),
        fieldWithPath("$prefix.roomStatus").type(JsonFieldType.STRING).description("룸 상태"),
    )
}
