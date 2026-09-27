package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.AttendanceResponse
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
    fun `방장이 확정 참여자 전원의 출석을 입력하며 룸을 완료한다`() {
        every { progressFacade.complete(hostId, roomId, attendances()) } returns RoomProgressCompletionResponse(
            "COMPLETED",
            listOf(
                AttendanceResponse(hostId, "영리한 부엉이 86", "ATTENDED"),
                AttendanceResponse(participantId, "성실한 사슴 03", "ABSENT"),
            ),
        )

        mockMvc.perform(completeRequest(completeBody()))
            .andExpect(status().isOk)
            .andExpect { assertThat(it.response.contentAsString).contains("\"status\":\"COMPLETED\"") }
            .andDo(
                documentApi(
                    "completeRoomProgress",
                    COMPLETE_SUMMARY,
                    COMPLETE_DESCRIPTION,
                    pathParameters(parameterWithName("roomId").description("완료할 룸 식별자")),
                    requestFields(
                        fieldWithPath("attendances").description("확정 참여자 전원의 출석 선택"),
                        fieldWithPath("attendances[].memberId").description("참여자 회원 식별자"),
                        fieldWithPath("attendances[].status").description("ATTENDED | ABSENT"),
                    ),
                    successResponseFields(
                        fieldWithPath("data.status").description("완료 후 룸 상태 (COMPLETED)"),
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
    fun `룸 완료 요청 형식 오류를 문서화한다`() {
        mockMvc.perform(completeRequest("""{"attendances":[{"memberId":"$hostId","status":"UNKNOWN"}]}"""))
            .andExpect(status().isBadRequest)
            .andDo(
                documentApi(
                    "completeRoomProgress-e400",
                    COMPLETE_SUMMARY,
                    COMPLETE_DESCRIPTION,
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `룸 완료 도메인 오류를 문서화한다`() {
        listOf(
            CoreErrorType.ROOM_NOT_FOUND,
            CoreErrorType.ROOM_FORBIDDEN,
            CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH,
            CoreErrorType.ROOM_PROGRESS_NOT_COMPLETABLE,
            CoreErrorType.ROOM_PROGRESS_ATTENDANCE_ALREADY_RECORDED,
        ).forEach { errorType ->
            every { progressFacade.complete(hostId, roomId, attendances()) } throws CoreException(errorType)

            mockMvc.perform(completeRequest(completeBody()))
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

    private fun completeRequest(body: String) = post("/v1/rooms/{roomId}/complete", roomId)
        .principal(principal)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)

    private fun attendances() = listOf(
        Attendance(hostId, AttendanceStatus.ATTENDED),
        Attendance(participantId, AttendanceStatus.ABSENT),
    )

    private fun completeBody(): String = jsonMapper().writeValueAsString(
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
            "방장이 확정 참여자 전원의 참석 여부를 입력하며 CONFIRMED 룸을 COMPLETED로 전환한다. " +
                "E400, E1405, E1406, E1706, E1707, E1708을 응답할 수 있다."
        const val MY_ATTENDANCE_SUMMARY = "내 출석 결과 조회"
        const val MY_ATTENDANCE_DESCRIPTION =
            "완료 후 기록된 자신의 참석 결과를 조회한다. E1405, E1703, E1704, E1705를 응답할 수 있다."
    }
}
