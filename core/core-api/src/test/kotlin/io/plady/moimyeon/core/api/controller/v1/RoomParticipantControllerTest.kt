package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.ParticipantAiSummaryResponse
import io.plady.moimyeon.core.api.controller.v1.response.ParticipantJobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomParticipantResponse
import io.plady.moimyeon.core.api.controller.v1.response.RoomParticipantsResponse
import io.plady.moimyeon.core.api.facade.RoomParticipantFacade
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.domain.participation.ParticipationSlots
import io.plady.moimyeon.core.domain.participation.RoomParticipantService
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class RoomParticipantControllerTest : RestDocsTest() {
    private lateinit var roomParticipantFacade: RoomParticipantFacade
    private lateinit var roomParticipantService: RoomParticipantService
    private val roomId = "01920000-0000-7000-8000-000000000001"
    private val viewerMemberId: UUID = UUID.randomUUID()
    private val principal = Principal { viewerMemberId.toString() }

    private val leaveSummary = "룸 나가기"
    private val leaveDescription =
        "참여자가 스스로 룸에서 나간다(「룸 참여」 §4.6). 모집 중에는 자유롭게 나갈 수 있고, 진행이 확정된 뒤에도 " +
            "현재 인원이 최소 진행 인원보다 많으면 나갈 수 있다. 나가면 자리가 비어 방장이 대기 신청을 수락할 수 있고, " +
            "모집 중이면 같은 룸에 다시 신청할 수 있다. " +
            "방장이 나가면 방장 자리가 자동으로 넘어간다(참여자 → 대기 신청자 순). 넘길 사람이 아무도 없으면 룸이 취소된다."

    private val participantsSummary = "참여자 명부 조회"
    private val participantsDescription =
        "룸에 속한 사람이 참여자 명단을 확인한다(「룸 참여」 §4.5). 방장을 맨 위에 두고 참여한 순서로 내려준다. " +
            "나가거나 내보내진 참여자는 명부에 없다. AI 이력서 요약은 룸의 원본 공개 여부와 무관하게 같은 룸 참여자에게 공개된다. " +
            "이력서 원본 URL 은 응답에 없다 - 제출 식별자와 열람 가능 여부만 내려가고 발급은 별도 API 가 맡는다. " +
            "실명·연락처·전달 사항은 어떤 경우에도 내려가지 않는다(§6). " +
            "방장과 참여자만 조회할 수 있고 신청자·제3자는 거부된다(E1419). 취소·종료된 룸에서도 이미 속한 사람은 계속 조회할 수 있다."

    private val participationSlotsSummary = "참여 슬롯 여유분 조회"
    private val participationSlotsDescription =
        "내가 참여 중인 룸이 몇 개고 몇 개 더 참여할 수 있는지 돌려준다(「룸 참여」 §4.1, 방장 포함). " +
            "활성 룸(모집 중 · 진행 확정 · 진행 중)의 참여만 세고 취소 · 완료된 룸과 처리 대기 중인 신청은 세지 않는다. " +
            "remaining 이 0 이면 신규 신청 · 수락 · 룸 생성이 모두 409(E1425)로 거부된다. " +
            "일정을 여러 개 골라 룸을 일괄 생성할 때의 최대 선택 수는 화면이 min(중복 생성 제한 remaining, 이 remaining) 으로 계산한다(「룸 생성」 §4.4). " +
            "서버가 min 을 합쳐 주지 않는다: 어느 한도에 걸렸는지에 따라 안내 문구가 갈린다."

    @BeforeEach
    fun setUp() {
        roomParticipantFacade = mockk()
        roomParticipantService = mockk()
        mockMvc = mockController(
            RoomParticipantController(roomParticipantFacade, roomParticipantService),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun participationSlots() {
        every { roomParticipantService.getParticipationSlots(viewerMemberId) } returns ParticipationSlots.of(2)

        mockMvc.perform(get("/v1/members/me/participation-slots").principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "participationSlots",
                    participationSlotsSummary,
                    participationSlotsDescription,
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.occupied").type(JsonFieldType.NUMBER)
                            .description("활성 룸에서 참여 중(JOINED)인 룸 수. 방장으로 만든 룸도 센다"),
                        fieldWithPath("data.limit").type(JsonFieldType.NUMBER).description("허용 개수 (3)"),
                        fieldWithPath("data.remaining").type(JsonFieldType.NUMBER)
                            .description("더 참여할 수 있는 개수. 0 이면 신청 · 수락 · 생성이 E1425 로 거부된다. 음수가 되지 않는다"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun participants() {
        every { roomParticipantFacade.getParticipants(any(), any()) } returns RoomParticipantsResponse(
            participants = listOf(
                RoomParticipantResponse(
                    memberId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    nickname = "든든한 곰 07",
                    isHost = true,
                    jobRoles = listOf(ParticipantJobRoleResponse(101L, "백엔드 개발")),
                    activitySummary = null,
                    aiSummary = ParticipantAiSummaryResponse(
                        status = "DONE",
                        text = "핀테크 백엔드 3년 차. 결제 정산 배치와 대사 경험.",
                    ),
                    resumeSubmissionId = 4101L,
                    canViewOriginal = false,
                ),
                RoomParticipantResponse(
                    memberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    nickname = "성실한 다람쥐 12",
                    isHost = false,
                    jobRoles = listOf(ParticipantJobRoleResponse(102L, "데이터 엔지니어")),
                    activitySummary = null,
                    aiSummary = ParticipantAiSummaryResponse(status = "PROCESSING", text = null),
                    resumeSubmissionId = 4102L,
                    canViewOriginal = false,
                ),
            ),
        )

        mockMvc.perform(
            get("/v1/rooms/{roomId}/participants", roomId)
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "roomParticipants",
                    participantsSummary,
                    participantsDescription,
                    pathParameters(
                        parameterWithName("roomId").description("명부를 조회할 룸 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.participants").type(JsonFieldType.ARRAY)
                            .description("참여자 명부 (방장 우선, 그다음 참여 순서)"),
                        fieldWithPath("data.participants[].memberId").type(JsonFieldType.STRING)
                            .description("참여자 회원 식별자 (UUID)"),
                        fieldWithPath("data.participants[].nickname").type(JsonFieldType.STRING)
                            .description("닉네임. 탈퇴한 회원이면 대체 문구가 내려간다"),
                        fieldWithPath("data.participants[].isHost").type(JsonFieldType.BOOLEAN)
                            .description("방장 여부"),
                        fieldWithPath("data.participants[].jobRoles").type(JsonFieldType.ARRAY)
                            .description("관심 직무 목록 (없으면 빈 배열)"),
                        fieldWithPath("data.participants[].jobRoles[].jobRoleId").type(JsonFieldType.NUMBER)
                            .description("관심 직무 id"),
                        fieldWithPath("data.participants[].jobRoles[].name").type(JsonFieldType.STRING)
                            .description("관심 직무명"),
                        fieldWithPath("data.participants[].activitySummary").type(JsonFieldType.STRING).optional()
                            .description("공개 가능한 활동 정보 (이 목록에서는 null, 공개 프로필 API에서 조회)"),
                        fieldWithPath("data.participants[].aiSummary").type(JsonFieldType.OBJECT).optional()
                            .description("이력서 AI 요약. 제출 이력서가 없으면 null"),
                        fieldWithPath("data.participants[].aiSummary.status").type(JsonFieldType.STRING).optional()
                            .description("AI 요약 상태 (PROCESSING | DONE). 생성 실패도 준비 중으로 내려간다"),
                        fieldWithPath("data.participants[].aiSummary.text").type(JsonFieldType.STRING).optional()
                            .description("AI 요약 내용 (DONE 일 때 제공)"),
                        fieldWithPath("data.participants[].resumeSubmissionId").type(JsonFieldType.NUMBER).optional()
                            .description("제출 이력서 식별자. 원본 열람 요청의 입력이며 URL 은 내려가지 않는다"),
                        fieldWithPath("data.participants[].canViewOriginal").type(JsonFieldType.BOOLEAN)
                            .description("이력서 원본을 열 수 있는지. 원본 공개 룸이고 진행이 확정됐으며 조회자가 확정 참여자일 때만 true"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun leaveRoom() {
        every { roomParticipantFacade.leave(any(), any()) } returns Unit

        mockMvc.perform(
            delete("/v1/rooms/{roomId}/participants/me", roomId)
                .principal(principal),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "roomLeave",
                    leaveSummary,
                    leaveDescription,
                    pathParameters(
                        parameterWithName("roomId").description("나갈 룸 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().ignored(),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun leaveRoomAtMinCapacity() {
        every { roomParticipantFacade.leave(any(), any()) } throws
            CoreException(CoreErrorType.ROOM_AT_MIN_CAPACITY)

        mockMvc.perform(
            delete("/v1/rooms/{roomId}/participants/me", roomId)
                .principal(principal),
        )
            .andExpect(status().isConflict)
            .andDo(
                documentApi(
                    "roomLeaveAtMinCapacity",
                    leaveSummary,
                    "진행이 확정된 룸에서 현재 인원이 최소 진행 인원과 같으면 나갈 수 없다(E1423). " +
                        "확정은 그 인원으로 진행한다는 약속이라, 여기서 더 빠지면 룸이 성립하지 않는다.",
                    pathParameters(
                        parameterWithName("roomId").description("나갈 룸 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (ERROR)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error.code").type(JsonFieldType.STRING).description("에러 코드 (E1423)"),
                        fieldWithPath("error.message").type(JsonFieldType.STRING).description("에러 메시지"),
                        fieldWithPath("error.data").type(JsonFieldType.NULL).optional().ignored(),
                    ),
                ),
            )
    }

    @Test
    fun leaveRoomNotParticipating() {
        every { roomParticipantFacade.leave(any(), any()) } throws
            CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        mockMvc.perform(
            delete("/v1/rooms/{roomId}/participants/me", roomId)
                .principal(principal),
        )
            .andExpect(status().isForbidden)
            .andDo(
                documentApi(
                    "roomLeaveNotParticipating",
                    leaveSummary,
                    "참여 중이 아니면 나갈 수 없다(E1419). 신청만 넣은 사용자와 이미 나간 사람이 모두 여기에 해당한다.",
                    pathParameters(
                        parameterWithName("roomId").description("나갈 룸 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (ERROR)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error.code").type(JsonFieldType.STRING).description("에러 코드 (E1419)"),
                        fieldWithPath("error.message").type(JsonFieldType.STRING).description("에러 메시지"),
                        fieldWithPath("error.data").type(JsonFieldType.NULL).optional().ignored(),
                    ),
                ),
            )
    }

    @Test
    fun participantsForbidden() {
        every { roomParticipantFacade.getParticipants(any(), any()) } throws
            CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        mockMvc.perform(
            get("/v1/rooms/{roomId}/participants", roomId)
                .principal(principal),
        )
            .andExpect(status().isForbidden)
            .andDo(
                documentApi(
                    "roomParticipantsForbidden",
                    participantsSummary,
                    "룸에 속하지 않은 사용자가 명부를 조회하면 거부된다. 신청만 넣은 사용자와 나간 참여자도 포함이다(E1419).",
                    pathParameters(
                        parameterWithName("roomId").description("명부를 조회할 룸 id (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (ERROR)"),
                        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
                        fieldWithPath("error.code").type(JsonFieldType.STRING).description("에러 코드 (E1419)"),
                        fieldWithPath("error.message").type(JsonFieldType.STRING).description("에러 메시지"),
                        fieldWithPath("error.data").type(JsonFieldType.NULL).optional().ignored(),
                    ),
                ),
            )
    }
}
