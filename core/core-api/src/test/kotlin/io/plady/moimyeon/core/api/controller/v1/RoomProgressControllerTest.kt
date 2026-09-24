package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.AttendanceResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomAttendancesResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomProgressCompletionResponse
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
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class RoomProgressControllerTest : RestDocsTest() {
    private lateinit var progressFacade: RoomProgressFacade
    private val roomId = UUID.fromString("01920000-0000-7000-8000-000000000440")
    private val hostId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val participantId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val principal = Principal { hostId.toString() }

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
    fun `방장이 확정 룸을 즉시 완료한다`() {
        every { progressFacade.complete(hostId, roomId) } returns RoomProgressCompletionResponse("COMPLETED")

        mockMvc.perform(post("/v1/rooms/{roomId}/complete", roomId).principal(principal))
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"status\":\"COMPLETED\"") }
            .andDo(
                documentApi(
                    "completeRoomProgress",
                    COMPLETE_SUMMARY,
                    COMPLETE_DESCRIPTION,
                    pathParameters(parameterWithName("roomId").description("완료할 룸 식별자")),
                    successResponseFields(fieldWithPath("data.status").description("완료 후 룸 상태 (COMPLETED)")),
                ),
            )
    }

    @Test
    fun `완료 이후 방장이 확정 참여자 전원의 출석을 기록한다`() {
        val attendances = listOf(
            Attendance(hostId, AttendanceStatus.ATTENDED),
            Attendance(participantId, AttendanceStatus.ABSENT),
        )
        every { progressFacade.recordAttendances(hostId, roomId, attendances) } returns RoomAttendancesResponse(
            listOf(
                AttendanceResponse(hostId, "영리한 부엉이 86", "ATTENDED"),
                AttendanceResponse(participantId, "성실한 사슴 03", "ABSENT"),
            ),
        )

        mockMvc.perform(
            post("/v1/rooms/{roomId}/attendances", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(attendanceRequest()),
        )
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"status\":\"ABSENT\"") }
            .andDo(
                documentApi(
                    "recordRoomAttendances",
                    ATTENDANCE_SUMMARY,
                    ATTENDANCE_DESCRIPTION,
                    pathParameters(parameterWithName("roomId").description("출석을 기록할 룸 식별자")),
                    requestFields(
                        fieldWithPath("attendances").description("확정 참여자 전원의 출석 선택"),
                        fieldWithPath("attendances[].memberId").description("참여자 회원 식별자"),
                        fieldWithPath("attendances[].status").description("ATTENDED | ABSENT"),
                    ),
                    successResponseFields(
                        fieldWithPath("data.attendances").description("저장된 출석 목록"),
                        fieldWithPath("data.attendances[].memberId").description("참여자 회원 식별자"),
                        fieldWithPath("data.attendances[].nickname").description("참여자 닉네임"),
                        fieldWithPath("data.attendances[].status").description("ATTENDED | ABSENT"),
                    ),
                ),
            )
    }

    @Test
    fun `완료된 룸에서 내 출석을 조회한다`() {
        every { progressFacade.getMyAttendance(hostId, roomId) } returns
            AttendanceResponse(hostId, "영리한 부엉이 86", "ATTENDED")

        mockMvc.perform(get("/v1/attendances/me").queryParam("roomId", roomId.toString()).principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "getMyAttendance",
                    MY_ATTENDANCE_SUMMARY,
                    MY_ATTENDANCE_DESCRIPTION,
                    queryParameters(parameterWithName("roomId").description("룸 식별자")),
                    successResponseFields(
                        fieldWithPath("data.memberId").description("로그인 회원 식별자"),
                        fieldWithPath("data.nickname").description("로그인 회원 닉네임"),
                        fieldWithPath("data.status").description("ATTENDED | ABSENT"),
                    ),
                ),
            )
    }

    @Test
    fun `룸 완료 오류를 문서화한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.ROOM_FORBIDDEN,
            CoreErrorType.ROOM_PROGRESS_NOT_COMPLETABLE,
        ).forEach { errorType ->
            every { progressFacade.complete(hostId, roomId) } throws CoreException(errorType)

            mockMvc.perform(post("/v1/rooms/{roomId}/complete", roomId).principal(principal))
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "completeRoomProgress-${errorType.code.name.lowercase()}",
                        COMPLETE_SUMMARY,
                        COMPLETE_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `출석 기록 요청 형식 오류를 문서화한다`() {
        mockMvc.perform(
            post("/v1/rooms/{roomId}/attendances", roomId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"attendances":[{"memberId":"$hostId","status":"UNKNOWN"}]}"""),
        )
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "recordRoomAttendances-e400",
                    ATTENDANCE_SUMMARY,
                    ATTENDANCE_DESCRIPTION,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `출석 기록 도메인 오류를 문서화한다`() {
        val attendances = listOf(
            Attendance(hostId, AttendanceStatus.ATTENDED),
            Attendance(participantId, AttendanceStatus.ABSENT),
        )
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.ROOM_FORBIDDEN,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
            CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH,
            CoreErrorType.ROOM_PROGRESS_ATTENDANCE_ALREADY_RECORDED,
        ).forEach { errorType ->
            every { progressFacade.recordAttendances(hostId, roomId, attendances) } throws CoreException(errorType)

            mockMvc.perform(
                post("/v1/rooms/{roomId}/attendances", roomId)
                    .principal(principal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(attendanceRequest()),
            )
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "recordRoomAttendances-${errorType.code.name.lowercase()}",
                        ATTENDANCE_SUMMARY,
                        ATTENDANCE_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    @Test
    fun `내 출석 조회 오류를 문서화한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.ROOM_PROGRESS_FORBIDDEN,
            CoreErrorType.ROOM_PROGRESS_NOT_AVAILABLE,
            CoreErrorType.ROOM_PROGRESS_ATTENDANCE_NOT_FOUND,
        ).forEach { errorType ->
            every { progressFacade.getMyAttendance(hostId, roomId) } throws CoreException(errorType)

            mockMvc.perform(get("/v1/attendances/me").queryParam("roomId", roomId.toString()).principal(principal))
                .andExpect(status().`is`(errorType.status.value()))
                .andDo(
                    documentApi(
                        "getMyAttendance-${errorType.code.name.lowercase()}",
                        MY_ATTENDANCE_SUMMARY,
                        MY_ATTENDANCE_DESCRIPTION,
                        errorResponseFields(),
                    ),
                )
        }
    }

    private fun attendanceRequest(): String = jsonMapper().writeValueAsString(
        mapOf(
            "attendances" to listOf(
                mapOf("memberId" to hostId, "status" to "ATTENDED"),
                mapOf("memberId" to participantId, "status" to "ABSENT"),
            ),
        ),
    )

    private companion object {
        const val COMPLETE_SUMMARY = "룸 완료"
        const val COMPLETE_DESCRIPTION =
            "방장이 CONFIRMED 룸을 즉시 COMPLETED로 전환한다. 출석은 별도 API로 기록한다. " +
                "E1405, E1406, E1707을 응답할 수 있다."
        const val ATTENDANCE_SUMMARY = "룸 출석 기록"
        const val ATTENDANCE_DESCRIPTION =
            "룸 완료 이후 방장이 최신 확정 참여자 전원의 참석 여부를 한 번 기록한다. " +
                "E400, E1405, E1406, E1704, E1706, E1708을 응답할 수 있다."
        const val MY_ATTENDANCE_SUMMARY = "내 출석 결과 조회"
        const val MY_ATTENDANCE_DESCRIPTION =
            "완료 후 기록된 자신의 참석 결과를 조회한다. E1405, E1703, E1704, E1705를 응답할 수 있다."
    }
}
