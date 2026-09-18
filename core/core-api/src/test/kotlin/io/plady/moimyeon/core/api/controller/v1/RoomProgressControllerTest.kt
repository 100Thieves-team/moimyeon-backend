package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.AttendanceResponse
import io.plady.moimyeon.core.api.controller.v1.response.ProgressBlockResponse
import io.plady.moimyeon.core.api.controller.v1.response.ProgressRailResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomProgressStartResponse
import io.plady.moimyeon.core.api.facade.RoomProgressFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.progress.Attendance
import io.plady.moimyeon.core.enums.AttendanceStatus
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
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class RoomProgressControllerTest : RestDocsTest() {
    private lateinit var progressFacade: RoomProgressFacade

    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000440")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val principal = Principal { memberId.toString() }

    @BeforeEach
    fun setUp() {
        progressFacade = mockk()
        mockMvc = mockController(
            RoomProgressController(progressFacade),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `출석 명단을 제출해 면접 진행을 시작한다`() {
        val attendances = listOf(
            Attendance(memberId, AttendanceStatus.ATTENDED),
            Attendance(otherMemberId, AttendanceStatus.ABSENT),
        )
        every { progressFacade.start(memberId, roomId, attendances) } returns RoomProgressStartResponse(
            status = "IN_PROGRESS",
            hostMemberId = memberId,
            attendances = listOf(
                AttendanceResponse(memberId, "영리한 부엉이 86", "ATTENDED"),
                AttendanceResponse(otherMemberId, "성실한 사슴 03", "ABSENT"),
            ),
        )

        mockMvc.perform(
            post("/v1/room-progresses")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf(
                            "roomId" to roomId,
                            "attendances" to listOf(
                                mapOf("memberId" to memberId, "status" to "ATTENDED"),
                                mapOf("memberId" to otherMemberId, "status" to "ABSENT"),
                            ),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"status\":\"IN_PROGRESS\"") }
            .andExpect { assertThat(it.response.contentAsString).contains(memberId.toString()) }
            .andExpect { assertThat(it.response.contentAsString).contains("\"status\":\"ABSENT\"") }
            .andDo(
                documentApi(
                    "startRoomProgress",
                    START_SUMMARY,
                    START_DESCRIPTION,
                    requestFields(
                        fieldWithPath("roomId").description("룸 id (UUID)"),
                        fieldWithPath("attendances").description("확정 참여자 전원의 출석 선택"),
                        fieldWithPath("attendances[].memberId").description("참여자 회원 id (UUID)"),
                        fieldWithPath("attendances[].status").description("ATTENDED | ABSENT"),
                    ),
                    successResponseFields(
                        fieldWithPath("data.status").description("시작 후 룸 상태 (IN_PROGRESS)"),
                        fieldWithPath("data.hostMemberId").description("진행을 시작한 방장 회원 id"),
                        fieldWithPath("data.attendances").description("확정된 출석 목록"),
                        fieldWithPath("data.attendances[].memberId").description("참여자 회원 id"),
                        fieldWithPath("data.attendances[].nickname").description("참여자 닉네임. 탈퇴한 회원은 대체 표기로 내려간다"),
                        fieldWithPath("data.attendances[].status").description("ATTENDED | ABSENT"),
                    ),
                ),
            )
    }

    @Test
    fun `진행 레일은 오프닝 라운드 클로징과 표시 이름을 반환한다`() {
        every { progressFacade.getRail(memberId, roomId) } returns ProgressRailResponse(
            blocks = listOf(
                ProgressBlockResponse(type = "OPENING", target = null),
                ProgressBlockResponse(
                    type = "ROUND",
                    target = io.plady.moimyeon.core.api.controller.v1.response.QuestionMemberResponse(
                        otherMemberId,
                        "성실한 사슴 03",
                    ),
                ),
                ProgressBlockResponse(type = "CLOSING", target = null),
            ),
        )

        mockMvc.perform(
            get("/v1/progress-rails").queryParam("roomId", roomId.toString()).principal(principal),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"type\":\"OPENING\"") }
            .andExpect { assertThat(it.response.contentAsString).contains("성실한 사슴 03") }
            .andExpect { assertThat(it.response.contentAsString).contains("\"type\":\"CLOSING\"") }
            .andDo(
                documentApi(
                    "getProgressRail",
                    RAIL_SUMMARY,
                    RAIL_DESCRIPTION,
                    queryParameters(parameterWithName("roomId").description("룸 id (UUID)")),
                    successResponseFields(
                        fieldWithPath("data.blocks").description("오프닝, 라운드, 클로징 순 진행 블록"),
                        fieldWithPath("data.blocks[].type").description("OPENING | ROUND | CLOSING"),
                        fieldWithPath("data.blocks[].target").type(JsonFieldType.OBJECT).optional()
                            .description("ROUND 블록의 면접 대상, 그 외 null"),
                        fieldWithPath("data.blocks[].target.memberId").optional().description("면접 대상 회원 id"),
                        fieldWithPath("data.blocks[].target.nickname").optional().description("면접 대상 표시 이름"),
                    ),
                ),
            )
    }

    @Test
    fun `본인의 출석 결과만 조회한다`() {
        every { progressFacade.getMyAttendance(memberId, roomId) } returns
            AttendanceResponse(memberId, "영리한 부엉이 86", "ATTENDED")

        mockMvc.perform(
            get("/v1/attendances/me").queryParam("roomId", roomId.toString()).principal(principal),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains(memberId.toString()) }
            .andExpect { assertThat(it.response.contentAsString).contains("\"status\":\"ATTENDED\"") }
            .andDo(
                documentApi(
                    "getMyAttendance",
                    ATTENDANCE_SUMMARY,
                    ATTENDANCE_DESCRIPTION,
                    queryParameters(parameterWithName("roomId").description("룸 id (UUID)")),
                    successResponseFields(
                        fieldWithPath("data.memberId").description("로그인 회원 id"),
                        fieldWithPath("data.nickname").description("로그인 회원 닉네임"),
                        fieldWithPath("data.status").description("ATTENDED | ABSENT"),
                    ),
                ),
            )
    }

    @Test
    fun `지원하지 않는 출석 상태는 E400`() {
        mockMvc.perform(
            post("/v1/room-progresses")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper().writeValueAsString(
                        mapOf(
                            "roomId" to roomId,
                            "attendances" to listOf(mapOf("memberId" to memberId, "status" to "LATE")),
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "startRoomProgress-e400",
                    START_SUMMARY,
                    START_DESCRIPTION,
                    errorResponseFields(),
                ),
            )

        verify(exactly = 0) { progressFacade.start(any(), any(), any()) }
    }

    @Test
    fun `진행 시작 도메인 오류를 문서화한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.ROOM_PROGRESS_NOT_STARTABLE,
            CoreErrorType.ROOM_PROGRESS_START_FORBIDDEN,
            CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH,
        ).forEach { errorType ->
            every { progressFacade.start(any(), roomId, any()) } throws CoreException(errorType)

            mockMvc.perform(
                post("/v1/room-progresses")
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validAttendanceRequest()),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "startRoomProgress-${errorType.code.name.lowercase()}",
                        START_SUMMARY,
                        START_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `진행 레일 조회 오류를 문서화한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
            CoreErrorType.ROOM_PROGRESS_FORBIDDEN,
        ).forEach { errorType ->
            every { progressFacade.getRail(memberId, roomId) } throws CoreException(errorType)

            mockMvc.perform(
                get("/v1/progress-rails").queryParam("roomId", roomId.toString()).principal(principal),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getProgressRail-${errorType.code.name.lowercase()}",
                        RAIL_SUMMARY,
                        RAIL_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `내 출석 조회 오류를 문서화한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
            CoreErrorType.ROOM_PROGRESS_FORBIDDEN,
            CoreErrorType.ROOM_PROGRESS_ATTENDANCE_NOT_FOUND,
        ).forEach { errorType ->
            every { progressFacade.getMyAttendance(memberId, roomId) } throws CoreException(errorType)

            mockMvc.perform(
                get("/v1/attendances/me").queryParam("roomId", roomId.toString()).principal(principal),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getMyAttendance-${errorType.code.name.lowercase()}",
                        ATTENDANCE_SUMMARY,
                        ATTENDANCE_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    private fun validAttendanceRequest(): String = jsonMapper().writeValueAsString(
        mapOf(
            "roomId" to roomId,
            "attendances" to listOf(
                mapOf("memberId" to memberId, "status" to "ATTENDED"),
                mapOf("memberId" to otherMemberId, "status" to "ABSENT"),
            ),
        ),
    )

    private companion object {
        const val START_SUMMARY = "면접 진행 시작"
        const val START_DESCRIPTION =
            "확정 참여자가 전원의 출석·불참을 제출해 룸을 한 번만 시작한다. " +
                "E400, E1405, E1701, E1702, E1706을 응답할 수 있다."
        const val RAIL_SUMMARY = "면접 진행 레일 조회"
        const val RAIL_DESCRIPTION =
            "오프닝, 확정 참여자 순 라운드, 클로징 블록을 반환한다. " +
                "E1405, E1703, E1704를 응답할 수 있다."
        const val ATTENDANCE_SUMMARY = "내 출석 결과 조회"
        const val ATTENDANCE_DESCRIPTION =
            "진행 시작 시 기록된 자신의 출석 결과를 조회한다. " +
                "E1405, E1703, E1704, E1705를 응답할 수 있다."
    }
}
