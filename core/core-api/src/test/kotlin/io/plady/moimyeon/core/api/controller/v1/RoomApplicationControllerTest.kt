package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.request.RejectApplicationRequest
import io.plady.moimyeon.core.api.controller.v1.response.ApplicantJobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.ApplicantResponse
import io.plady.moimyeon.core.api.controller.v1.response.ApplicationAiSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomApplicationsResponse
import io.plady.moimyeon.core.api.facade.RoomApplicationFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeSummary
import io.plady.moimyeon.core.domain.room.ApplicationDecision
import io.plady.moimyeon.core.domain.room.RoomApplicationService
import io.plady.moimyeon.core.domain.roomapplication.ResumeSubmission
import io.plady.moimyeon.core.domain.roomapplication.RoomApplication
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationForm
import io.plady.moimyeon.core.domain.roomapplication.RoomApplicationSubmissionService
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
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
    private lateinit var roomApplicationService: RoomApplicationService
    private lateinit var roomApplicationSubmissionService: RoomApplicationSubmissionService
    private val roomId = "01920000-0000-7000-8000-000000000001"
    private val hostMemberId: UUID = UUID.randomUUID()
    private val applicantMemberId: UUID = UUID.randomUUID()
    private val resumeId: UUID = UUID.randomUUID()
    private val principal = Principal { hostMemberId.toString() }
    private val applicantPrincipal = Principal { applicantMemberId.toString() }

    private val applicationsSummary = "참가 신청 목록 조회 (방장)"
    private val applicationsDescription =
        "방장이 룸 관리 화면에서 참가 신청 목록을 확인한다(「룸 참여」 §4.3). 신청자의 공개 정보·전달 사항·처리 상태를 신청 시각 오름차순으로 내려준다. " +
            "철회된 신청은 제외된다. 전달 사항은 방장 외 비공개이며, 이력서 원본으로 가는 경로는 없다(§6). 방장만 조회할 수 있다(E1406). " +
            "관심 직무는 목록으로 제공한다. 공개 활동 정보는 이 목록에서는 null이며 공개 프로필 API에서 조회한다."
    private val acceptSummary = "참가 신청 수락"
    private val acceptDescription =
        "방장이 대기 중인 신청을 수락한다(「룸 참여」 §4.4). 서버가 정원을 최종 확인한 뒤 신청자를 참여자로 등록하고 현재 인원을 증가시킨다. " +
            "정원에 도달하면 모집 상태가 마감(CLOSED)으로 계산된다. 마지막 자리를 두고 동시 수락이 몰려도 1건만 성공한다(룸 행 잠금). " +
            "방장만 처리할 수 있고(E1406), 이미 처리된 신청(E1409)·모집 중이 아닌 방(E1410)·정원 초과(E1411)는 거부된다."
    private val rejectSummary = "참가 신청 반려"
    private val rejectDescription =
        "방장이 대기 중인 신청을 반려한다(§4.4). 사유는 선택(최대 50자)이며, 반려는 정원·참여자 목록에 영향이 없다. " +
            "반려된 사용자는 같은 룸에 재신청할 수 없다. 방장만 처리할 수 있다(E1406)."
    private val submitSummary = "참가 신청 제출"
    private val submitDescription =
        "로그인 회원이 보관 중인 이력서 하나와 선택 전달 사항으로 참가 신청을 제출한다(「룸 참여」 §4.2). " +
            "신청은 PENDING으로 저장되고 참여 인원은 증가하지 않는다. 전달 사항이 300자를 초과하면 E400, 회원·이력서·룸·중복·재신청·대기 한도 검증 실패는 " +
            "E1002, E1010, E1405, E1410, E1412, E1413, E1414, E1415, E1416으로 구분한다."
    private val myApplicationSummary = "내 참가 신청 조회"
    private val myApplicationDescription =
        "로그인 회원이 해당 룸에 가장 최근 제출한 신청의 상태, 전달 사항, 제출 당시 이력서와 AI 요약을 확인한다. " +
            "신청이 없으면 E1408을 반환한다."
    private val withdrawSummary = "참가 신청 철회"
    private val withdrawDescription =
        "로그인 회원이 방장 처리 전 PENDING 신청을 철회한다. 신청이 없으면 E1408, 이미 처리됐으면 E1409를 반환한다."

    @BeforeEach
    fun setUp() {
        roomApplicationFacade = mockk()
        roomApplicationService = mockk()
        roomApplicationSubmissionService = mockk()
        mockMvc = mockController(
            RoomApplicationController(roomApplicationFacade, roomApplicationService, roomApplicationSubmissionService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `보관 이력서와 전달 사항으로 참가 신청을 제출한다`() {
        every {
            roomApplicationSubmissionService.submit(
                applicantMemberId,
                UUID.fromString(roomId),
                RoomApplicationForm(resumeId, "결제 도메인 면접을 실전처럼 준비하고 싶어요."),
            )
        } returns 3003L
        val request = mapOf(
            "resumeId" to resumeId,
            "note" to "결제 도메인 면접을 실전처럼 준비하고 싶어요.",
        )

        mockMvc.perform(
            post("/v1/rooms/{roomId}/applications", roomId)
                .principal(applicantPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isCreated)
            .andDo(
                documentApi(
                    "submitRoomApplication",
                    submitSummary,
                    submitDescription,
                    pathParameters(
                        parameterWithName("roomId").description("참가 신청할 룸 id (UUID)"),
                    ),
                    requestFields(
                        fieldWithPath("resumeId").type(JsonFieldType.STRING).description("제출할 본인 보관 이력서 id (UUID)"),
                        fieldWithPath("note").type(JsonFieldType.STRING).optional()
                            .description("방장에게 전할 말 (선택, 최대 300자, 미입력 시 빈 문자열)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.applicationId").type(JsonFieldType.NUMBER).description("생성된 참가 신청 id"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("신청 상태 (PENDING)"),
                        fieldWithPath("data.statusLabel").type(JsonFieldType.STRING).description("신청 상태 표시명 (대기 중)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `전달 사항을 생략하면 빈 문자열로 신청한다`() {
        every {
            roomApplicationSubmissionService.submit(
                applicantMemberId,
                UUID.fromString(roomId),
                RoomApplicationForm(resumeId, ""),
            )
        } returns 3004L

        mockMvc.perform(
            post("/v1/rooms/{roomId}/applications", roomId)
                .principal(applicantPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("resumeId" to resumeId))),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `전달 사항이 300자면 신청한다`() {
        val note = "가".repeat(300)
        every {
            roomApplicationSubmissionService.submit(
                applicantMemberId,
                UUID.fromString(roomId),
                RoomApplicationForm(resumeId, note),
            )
        } returns 3005L

        mockMvc.perform(
            post("/v1/rooms/{roomId}/applications", roomId)
                .principal(applicantPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(mapOf("resumeId" to resumeId, "note" to note))),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `전달 사항이 300자를 초과하면 E400을 반환한다`() {
        val request = mapOf(
            "resumeId" to resumeId,
            "note" to "가".repeat(301),
        )

        mockMvc.perform(
            post("/v1/rooms/{roomId}/applications", roomId)
                .principal(applicantPrincipal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "submitRoomApplication-e400-note-length",
                    submitSummary,
                    submitDescription,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `해당 룸에 제출한 내 최신 참가 신청을 조회한다`() {
        every { roomApplicationSubmissionService.getMyApplication(applicantMemberId, any()) } returns myApplication()

        mockMvc.perform(
            get("/v1/rooms/{roomId}/applications/me", roomId)
                .principal(applicantPrincipal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "myRoomApplication",
                    myApplicationSummary,
                    myApplicationDescription,
                    pathParameters(
                        parameterWithName("roomId").description("내 참가 신청을 조회할 룸 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.applicationId").type(JsonFieldType.NUMBER).description("참가 신청 id"),
                        fieldWithPath("data.roomId").type(JsonFieldType.STRING).description("룸 id (UUID)"),
                        fieldWithPath("data.note").type(JsonFieldType.STRING).description("내가 입력한 전달 사항 (미입력 시 빈 문자열)"),
                        fieldWithPath("data.resume.resumeId").type(JsonFieldType.STRING).description("제출 원본이 된 보관 이력서 id (UUID)"),
                        fieldWithPath("data.resume.file.originalName").type(JsonFieldType.STRING).description("제출 당시 파일명"),
                        fieldWithPath("data.resume.file.sizeBytes").type(JsonFieldType.NUMBER).description("제출 당시 파일 크기 (bytes)"),
                        fieldWithPath("data.resume.file.contentType").type(JsonFieldType.STRING).description("제출 당시 파일 MIME 타입"),
                        fieldWithPath("data.resume.aiSummary.status").type(JsonFieldType.STRING)
                            .description("AI 요약 상태 (PROCESSING | DONE | FAILED)"),
                        fieldWithPath("data.resume.aiSummary.text").type(JsonFieldType.STRING).optional()
                            .description("AI 요약 내용 (DONE일 때 제공)"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING)
                            .description("신청 상태 (PENDING | ACCEPTED | REJECTED | WITHDRAWN | ROOM_CANCELED | ROOM_CONFIRMED)"),
                        fieldWithPath("data.statusLabel").type(JsonFieldType.STRING).description("신청자에게 표시할 상태명"),
                        fieldWithPath("data.appliedAt").type(JsonFieldType.STRING).description("신청 시각 (yyyy-MM-ddTHH:mm:ss)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun `방장 처리 전 내 참가 신청을 철회한다`() {
        justRun { roomApplicationSubmissionService.withdraw(applicantMemberId, any()) }

        mockMvc.perform(
            delete("/v1/rooms/{roomId}/applications/me", roomId)
                .principal(applicantPrincipal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "withdrawRoomApplication",
                    withdrawSummary,
                    withdrawDescription,
                    pathParameters(
                        parameterWithName("roomId").description("내 참가 신청을 철회할 룸 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
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
                        jobRoles = listOf(
                            ApplicantJobRoleResponse(101L, "백엔드 개발"),
                            ApplicantJobRoleResponse(102L, "데이터 엔지니어"),
                        ),
                        activitySummary = null,
                    ),
                    note = "결제 도메인 1차 면접을 앞두고 있어요. 실전처럼 연습하고 싶어 신청합니다.",
                    aiSummary = ApplicationAiSummaryResponse(
                        status = "DONE",
                        text = "결제 도메인 경험이 있는 백엔드 개발자",
                    ),
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
                        fieldWithPath("data.applications[].applicant.jobRoles").type(JsonFieldType.ARRAY).description("신청자의 관심 직무 목록 (없으면 빈 배열)"),
                        fieldWithPath("data.applications[].applicant.jobRoles[].jobRoleId").type(JsonFieldType.NUMBER).description("관심 직무 id"),
                        fieldWithPath("data.applications[].applicant.jobRoles[].name").type(JsonFieldType.STRING).description("관심 직무명"),
                        fieldWithPath("data.applications[].applicant.activitySummary").type(JsonFieldType.STRING).optional()
                            .description("공개 가능한 활동 정보 (이 목록에서는 null, 공개 프로필 API에서 조회)"),
                        fieldWithPath("data.applications[].note").type(JsonFieldType.STRING).description("전달 사항 (미입력 시 빈 문자열, 방장 외 비공개)"),
                        fieldWithPath("data.applications[].aiSummary").type(JsonFieldType.OBJECT).description("이력서 AI 요약 상태와 내용"),
                        fieldWithPath("data.applications[].aiSummary.status").type(JsonFieldType.STRING).description("AI 요약 상태 (PROCESSING | DONE)"),
                        fieldWithPath("data.applications[].aiSummary.text").type(JsonFieldType.STRING).optional().description("AI 요약 내용 (DONE일 때 제공)"),
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
        every { roomApplicationService.accept(any(), any(), any()) } returns ApplicationDecision(
            applicationId = 3001L,
            status = RoomApplicationStatus.ACCEPTED,
            currentParticipants = 2,
            maxCapacity = 6,
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
        every { roomApplicationService.reject(any(), any(), any(), any()) } returns ApplicationDecision(
            applicationId = 3001L,
            status = RoomApplicationStatus.REJECTED,
            currentParticipants = 1,
            maxCapacity = 6,
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

    private fun myApplication(): RoomApplication {
        return RoomApplication(
            id = 3003L,
            roomId = UUID.fromString(roomId),
            applicantMemberId = applicantMemberId,
            note = "결제 도메인 면접을 실전처럼 준비하고 싶어요.",
            resumeSubmission = ResumeSubmission(
                sourceResumeId = resumeId,
                file = ResumeFile(
                    key = "resumes/$applicantMemberId/source.pdf",
                    originalName = "backend.pdf",
                    sizeBytes = 2048L,
                    contentType = "application/pdf",
                ),
            ),
            resumeSummary = ResumeSummary(
                status = ResumeSummaryStatus.DONE,
                content = "Kotlin·Spring 백엔드 개발 경험과 결제 도메인 경험이 있습니다.",
            ),
            status = RoomApplicationStatus.PENDING,
            appliedAt = LocalDateTime.of(2026, 8, 13, 14, 30),
        )
    }
}
