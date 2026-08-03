package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.RejectApplicationRequest
import io.plady.moimyeon.core.api.controller.v1.response.ApplicantResponse
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationDecisionResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationsResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomRecruitSummaryResponse
import io.plady.moimyeon.core.api.facade.RoomApplicationFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
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
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID

class RoomApplicationControllerTest : RestDocsTest() {
    private lateinit var roomApplicationFacade: RoomApplicationFacade
    private val roomId = "01920000-0000-7000-8000-000000000001"
    private val hostMemberId: UUID = UUID.randomUUID()
    private val principal = Principal { hostMemberId.toString() }

    private val applicationsSummary = "참가 신청 목록 조회 (방장)"
    private val applicationsDescription =
        "방장이 룸 관리 화면에서 참가 신청 목록을 확인한다(「룸 참여」 §4.3). 신청자의 공개 정보·전달 사항·처리 상태를 신청 시각 오름차순으로 내려준다. " +
            "철회된 신청은 제외된다. 전달 사항은 방장 외 비공개이며, 이력서 원본으로 가는 경로는 없다(§6). 방장만 조회할 수 있다(E1406). " +
            "이력서 AI 요약(J5)·신청자 직무·활동 정보는 연동 전까지 null 로 내려간다."
    private val acceptSummary = "참가 신청 수락"
    private val acceptDescription =
        "방장이 대기 중인 신청을 수락한다(「룸 참여」 §4.4). 서버가 정원을 최종 확인한 뒤 신청자를 참여자로 등록하고 현재 인원을 증가시킨다. " +
            "정원에 도달하면 모집 상태가 마감(CLOSED)으로 계산된다. 마지막 자리를 두고 동시 수락이 몰려도 1건만 성공한다(룸 행 잠금). " +
            "방장만 처리할 수 있고(E1406), 이미 처리된 신청(E1409)·모집 중이 아닌 방(E1410)·정원 초과(E1411)는 거부된다."
    private val rejectSummary = "참가 신청 반려"
    private val rejectDescription =
        "방장이 대기 중인 신청을 반려한다(§4.4). 사유는 선택(최대 50자)이며, 반려는 정원·참여자 목록에 영향이 없다. " +
            "반려된 사용자는 같은 룸에 재신청할 수 없다. 방장만 처리할 수 있다(E1406)."

    @BeforeEach
    fun setUp() {
        roomApplicationFacade = mockk()
        mockMvc = mockController(
            RoomApplicationController(roomApplicationFacade),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun applications() {
        every { roomApplicationFacade.getApplications(any(), any()) } returns RoomApplicationsResponse(
            applications = listOf(
                RoomApplicationResponse(
                    applicationId = 3001L,
                    applicant = ApplicantResponse(
                        memberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        nickname = "성실한 다람쥐 12",
                        jobTitle = null,
                        activitySummary = null,
                    ),
                    note = "결제 도메인 1차 면접을 앞두고 있어요. 실전처럼 연습하고 싶어 신청합니다.",
                    aiSummary = null,
                    status = "PENDING",
                    statusLabel = "대기",
                    appliedAt = LocalDateTime.of(2026, 7, 31, 10, 20, 0),
                ),
            ),
        )

        mockMvc.perform(
            get("/v1/rooms/{roomId}/applications", roomId)
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "roomApplications",
                    applicationsSummary,
                    applicationsDescription,
                    pathParameters(
                        parameterWithName("roomId").description("신청 목록을 조회할 룸 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.applications").type(JsonFieldType.ARRAY).description("참가 신청 목록 (신청 시각 오름차순, 철회 제외)"),
                        fieldWithPath("data.applications[].applicationId").type(JsonFieldType.NUMBER).description("신청 id (수락·반려의 입력)"),
                        fieldWithPath("data.applications[].applicant.memberId").type(JsonFieldType.STRING).description("신청자 회원 식별자 (UUID)"),
                        fieldWithPath("data.applications[].applicant.nickname").type(JsonFieldType.STRING).description("신청자 닉네임"),
                        fieldWithPath("data.applications[].applicant.jobTitle").type(JsonFieldType.STRING).optional().description("신청자 직무 (연동 전까지 null)"),
                        fieldWithPath("data.applications[].applicant.activitySummary").type(JsonFieldType.STRING).optional()
                            .description("공개 가능한 활동 정보 (trust 격벽 전까지 null)"),
                        fieldWithPath("data.applications[].note").type(JsonFieldType.STRING).optional().description("전달 사항 (선택, 방장 외 비공개)"),
                        fieldWithPath("data.applications[].aiSummary").type(JsonFieldType.OBJECT).optional().description("이력서 AI 요약 (연동(J5) 전까지 null)"),
                        fieldWithPath("data.applications[].status").type(JsonFieldType.STRING).description("신청 상태 (PENDING | ACCEPTED | REJECTED)"),
                        fieldWithPath("data.applications[].statusLabel").type(JsonFieldType.STRING).description("신청 상태 표시명"),
                        fieldWithPath("data.applications[].appliedAt").type(JsonFieldType.STRING).description("신청 시각 (yyyy-MM-ddTHH:mm:ss)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun acceptApplication() {
        every { roomApplicationFacade.accept(any(), any(), any()) } returns ApplicationDecisionResponse(
            applicationId = 3001L,
            status = "ACCEPTED",
            statusLabel = "수락",
            recruit = RoomRecruitSummaryResponse(
                current = 2,
                max = 6,
                recruitStatus = "RECRUITING",
                recruitStatusLabel = "모집 중",
            ),
        )

        mockMvc.perform(
            post("/v1/rooms/{roomId}/applications/{applicationId}/accept", roomId, 3001L)
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "acceptApplication",
                    acceptSummary,
                    acceptDescription,
                    pathParameters(
                        parameterWithName("roomId").description("룸 id (UUID)"),
                        parameterWithName("applicationId").description("수락할 신청 id"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.applicationId").type(JsonFieldType.NUMBER).description("처리된 신청 id"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("신청 상태 (ACCEPTED)"),
                        fieldWithPath("data.statusLabel").type(JsonFieldType.STRING).description("신청 상태 표시명 (수락)"),
                        fieldWithPath("data.recruit.current").type(JsonFieldType.NUMBER).description("수락 반영 후 현재 인원"),
                        fieldWithPath("data.recruit.max").type(JsonFieldType.NUMBER).description("최대 인원"),
                        fieldWithPath("data.recruit.recruitStatus").type(JsonFieldType.STRING).description("모집 상태 (RECRUITING | CLOSED, 정원 충족 시 CLOSED)"),
                        fieldWithPath("data.recruit.recruitStatusLabel").type(JsonFieldType.STRING).description("모집 상태 표시명"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun rejectApplication() {
        every { roomApplicationFacade.reject(any(), any(), any(), any()) } returns ApplicationDecisionResponse(
            applicationId = 3001L,
            status = "REJECTED",
            statusLabel = "반려",
            recruit = RoomRecruitSummaryResponse(
                current = 1,
                max = 6,
                recruitStatus = "RECRUITING",
                recruitStatusLabel = "모집 중",
            ),
        )
        val request = RejectApplicationRequest(reason = "이번 일정과 준비 방향이 맞지 않아요.")

        mockMvc.perform(
            post("/v1/rooms/{roomId}/applications/{applicationId}/reject", roomId, 3001L)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "rejectApplication",
                    rejectSummary,
                    rejectDescription,
                    pathParameters(
                        parameterWithName("roomId").description("룸 id (UUID)"),
                        parameterWithName("applicationId").description("반려할 신청 id"),
                    ),
                    requestFields(
                        fieldWithPath("reason").type(JsonFieldType.STRING).optional().description("반려 사유 (선택, 최대 50자)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.applicationId").type(JsonFieldType.NUMBER).description("처리된 신청 id"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("신청 상태 (REJECTED)"),
                        fieldWithPath("data.statusLabel").type(JsonFieldType.STRING).description("신청 상태 표시명 (반려)"),
                        fieldWithPath("data.recruit.current").type(JsonFieldType.NUMBER).description("현재 인원 (반려는 변동 없음)"),
                        fieldWithPath("data.recruit.max").type(JsonFieldType.NUMBER).description("최대 인원"),
                        fieldWithPath("data.recruit.recruitStatus").type(JsonFieldType.STRING).description("모집 상태 (RECRUITING | CLOSED)"),
                        fieldWithPath("data.recruit.recruitStatusLabel").type(JsonFieldType.STRING).description("모집 상태 표시명"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
