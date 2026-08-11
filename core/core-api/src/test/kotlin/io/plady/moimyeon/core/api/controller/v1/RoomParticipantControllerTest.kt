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
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    private val roomId = "01920000-0000-7000-8000-000000000001"
    private val viewerMemberId: UUID = UUID.randomUUID()
    private val principal = Principal { viewerMemberId.toString() }

    private val participantsSummary = "참여자 명부 조회"
    private val participantsDescription =
        "룸에 속한 사람이 참여자 명단을 확인한다(「룸 참여」 §4.5). 방장을 맨 위에 두고 참여한 순서로 내려준다. " +
            "나가거나 내보내진 참여자는 명부에 없다. AI 이력서 요약은 룸의 원본 공개 여부와 무관하게 같은 룸 참여자에게 공개된다. " +
            "이력서 원본 URL 은 응답에 없다 - 제출 식별자와 열람 가능 여부만 내려가고 발급은 별도 API 가 맡는다. " +
            "실명·연락처·전달 사항은 어떤 경우에도 내려가지 않는다(§6). " +
            "방장과 참여자만 조회할 수 있고 신청자·제3자는 거부된다(E1419). 취소·종료된 룸에서도 이미 속한 사람은 계속 조회할 수 있다."

    @BeforeEach
    fun setUp() {
        roomParticipantFacade = mockk()
        mockMvc = mockController(
            RoomParticipantController(roomParticipantFacade),
            LoginMemberArgumentResolver(),
            controllerAdvice = ApiControllerAdvice(),
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
                            .description("공개 가능한 활동 정보 (trust 격벽 전까지 null)"),
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
