package io.plady.moimyeon.core.api.controller.v1

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
import io.plady.moimyeon.core.api.controller.v1.response.JobRoleResponse
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileResponse
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileTagResponse
import io.plady.moimyeon.core.api.controller.v1.response.PublicProfileTrustResponse
import io.plady.moimyeon.core.api.facade.PublicProfileFacade
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.test.api.RestDocsTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class PublicProfileControllerTest : RestDocsTest() {
    private lateinit var publicProfileFacade: PublicProfileFacade
    private val memberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val withdrawnMemberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000410")
    private val unknownMemberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000404")

    private val publicProfileSummary = "공개 프로필 조회"
    private val publicProfileDescription =
        "인증 없이 다른 사용자의 공개 프로필과 신뢰 지표를 조회한다. " +
            "닉네임·관심 직무·자기소개만 프로필 정보로 노출하며, " +
            "관심 회사·이메일·OAuth 식별자·약관 동의 이력은 담지 않는다. " +
            "존재하지 않거나 탈퇴한 회원, 필수 프로필이 없는 회원은 404(E1006), " +
            "잘못된 UUID는 400(E400)으로 응답한다."

    @BeforeEach
    fun setUp() {
        publicProfileFacade = mockk()
        mockMvc = mockController(
            PublicProfileController(publicProfileFacade),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `인증 없이 공개 프로필을 조회하면 공개 필드와 신뢰 지표를 반환한다`() {
        every { publicProfileFacade.get(memberId) } returns publicProfileResponse()

        mockMvc.perform(get("/v1/members/{memberId}/profile", memberId))
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"memberId\":\"$memberId\"")
                    .contains("\"nickname\":\"차분한 펭귄 12\"")
                    .contains(
                        "\"interestJobRoles\":[" +
                            "{\"jobRoleId\":1,\"code\":\"BACKEND_DEVELOPER\",\"displayName\":\"백엔드 개발자\"}," +
                            "{\"jobRoleId\":2,\"code\":\"FRONTEND_DEVELOPER\",\"displayName\":\"프론트엔드 개발자\"}]",
                    )
                    .contains("\"bio\":\"자기소개\"")
                    .contains("\"activityTopPercent\":12")
                    .contains("\"recentAttendances\":[\"ATTENDED\",\"ABSENT\",\"ATTENDED\"]")
                    .contains("\"noShowCount\":2")
                    .contains("\"label\":\"좋은 질문을 해요\",\"count\":5")
                    .doesNotContain("\"withdrawn\"")
                    .doesNotContain("\"jobTitle\"")
                    .doesNotContain("\"recentActivities\"")
                    .doesNotContain("\"completedRoomCount\"")
                    .doesNotContain("\"attendanceRate\"")
                    .doesNotContain("\"averageRating\"")
                    .doesNotContain("\"interestCompanies\"")
                    .doesNotContain("\"meetingPreference\"")
                    .doesNotContain("\"sigunguId\"")
                    .doesNotContain("\"email\"")
                    .doesNotContain("\"socialAccounts\"")
                    .doesNotContain("\"termsAgreements\"")
                    .doesNotContain("\"interviewStage\"")
                    .doesNotContain("\"interestJobRoleIds\"")
            }
            .andDo(
                documentApi(
                    "publicProfile",
                    publicProfileSummary,
                    publicProfileDescription,
                    publicProfilePathParameters(),
                    publicProfileResponseFields(),
                ),
            )
    }

    @Test
    fun `활동 이력이 없으면 비어 있는 신뢰 지표를 반환한다`() {
        every { publicProfileFacade.get(memberId) } returns publicProfileResponse(
            trust = PublicProfileTrustResponse(
                activityTopPercent = null,
                recentAttendances = emptyList(),
                noShowCount = 0,
                representativeTags = emptyList(),
            ),
        )

        mockMvc.perform(get("/v1/members/{memberId}/profile", memberId))
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"trust\":{")
                    .contains("\"activityTopPercent\":null")
                    .contains("\"recentAttendances\":[]")
                    .contains("\"noShowCount\":0")
                    .contains("\"representativeTags\":[]")
            }
    }

    @Test
    fun `존재하지 않는 회원의 공개 프로필은 E1006 을 반환한다`() {
        every { publicProfileFacade.get(unknownMemberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        mockMvc.perform(get("/v1/members/{memberId}/profile", unknownMemberId))
            .andExpect(status().isNotFound)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1006\"")
            }
            .andDo(
                documentApi(
                    "publicProfile-e1006",
                    publicProfileSummary,
                    publicProfileDescription,
                    publicProfilePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    @Test
    fun `탈퇴한 회원의 공개 프로필은 E1006 을 반환한다`() {
        every { publicProfileFacade.get(withdrawnMemberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        mockMvc.perform(get("/v1/members/{memberId}/profile", withdrawnMemberId))
            .andExpect(status().isNotFound)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E1006\"")
            }
    }

    @Test
    fun `회원 식별자가 UUID 형식이 아니면 E400 을 반환한다`() {
        mockMvc.perform(get("/v1/members/{memberId}/profile", "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect { result ->
                assertThat(result.response.contentAsString).contains("\"code\":\"E400\"")
            }
            .andDo(
                documentApi(
                    "publicProfile-e400",
                    publicProfileSummary,
                    publicProfileDescription,
                    publicProfilePathParameters(),
                    errorResponseFields(),
                ),
            )
    }

    private fun publicProfileResponse(
        trust: PublicProfileTrustResponse = PublicProfileTrustResponse(
            activityTopPercent = 12,
            recentAttendances = listOf(AttendanceStatus.ATTENDED, AttendanceStatus.ABSENT, AttendanceStatus.ATTENDED),
            noShowCount = 2,
            representativeTags = listOf(PublicProfileTagResponse(label = "좋은 질문을 해요", count = 5)),
        ),
    ): PublicProfileResponse {
        return PublicProfileResponse(
            memberId = memberId,
            nickname = "차분한 펭귄 12",
            interestJobRoles = listOf(
                JobRoleResponse(1L, "BACKEND_DEVELOPER", "백엔드 개발자"),
                JobRoleResponse(2L, "FRONTEND_DEVELOPER", "프론트엔드 개발자"),
            ),
            bio = "자기소개",
            trust = trust,
        )
    }

    private fun publicProfilePathParameters() = pathParameters(
        parameterWithName("memberId").description("조회할 회원 식별자 (UUID)"),
    )

    private fun publicProfileResponseFields() = responseFields(
        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("닉네임"),
        fieldWithPath("data.interestJobRoles").type(JsonFieldType.ARRAY).description("관심 직무 목록"),
        fieldWithPath("data.interestJobRoles[].jobRoleId").type(JsonFieldType.NUMBER).description("관심 직무 식별자"),
        fieldWithPath("data.interestJobRoles[].code").type(JsonFieldType.STRING).description("관심 직무 코드"),
        fieldWithPath("data.interestJobRoles[].displayName").type(JsonFieldType.STRING).description("관심 직무 표시명"),
        fieldWithPath("data.bio").type(JsonFieldType.STRING).description("자기소개 (미지정이면 빈 문자열)"),
        fieldWithPath("data.trust").type(JsonFieldType.OBJECT).description("신뢰 지표 (활동 이력이 없어도 항상 반환)"),
        fieldWithPath("data.trust.activityTopPercent").type(JsonFieldType.NUMBER).optional()
            .description("활동률 상위 퍼센트 (출석한 완료 룸이 없으면 null)"),
        fieldWithPath("data.trust.recentAttendances").type(JsonFieldType.ARRAY)
            .description("최근 완료 룸 출석 결과, 최신순 최대 3건"),
        fieldWithPath("data.trust.noShowCount").type(JsonFieldType.NUMBER).description("완료 룸 누적 불참 횟수"),
        fieldWithPath("data.trust.representativeTags").type(JsonFieldType.ARRAY)
            .description("대표 평가 태그, 받은 횟수 내림차순·문구 오름차순 최대 3개"),
        fieldWithPath("data.trust.representativeTags[].label").type(JsonFieldType.STRING).description("평가 문구"),
        fieldWithPath("data.trust.representativeTags[].count").type(JsonFieldType.NUMBER).description("받은 횟수"),
        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
    )
}
