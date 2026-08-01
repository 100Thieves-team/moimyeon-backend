package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.controller.ApiControllerAdvice
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
    private val activeMemberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val withdrawnMemberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000410")
    private val unknownMemberId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000404")

    private val publicProfileSummary = "공개 프로필 조회"
    private val publicProfileDescription =
        "다른 사용자가 보는 공개 프로필로 닉네임·직무·자기소개·공개 활동만 노출하고, " +
            "이메일·OAuth 식별자·약관 동의 이력·비공개 관심 정보는 담지 않는다. " +
            "목 API는 활성 회원($activeMemberId)과 탈퇴 회원($withdrawnMemberId)을 고정 예시로 제공하며, " +
            "그 밖의 회원은 404(E1006)로 응답한다. 신뢰 정보는 목에서만 예시 값을 채우고, " +
            "실 API는 이번 스프린트에 data.trust를 null로 반환한다. 잘못된 UUID는 400(E400)으로 응답한다."

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(
            PublicProfileController(),
            controllerAdvice = ApiControllerAdvice(),
        )
    }

    @Test
    fun `다른 사용자의 공개 프로필을 조회하면 공개 정보와 목 신뢰 지표를 반환한다`() {
        mockMvc.perform(get("/v1/members/{memberId}/profile", activeMemberId))
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"nickname\":\"성실한 사슴 03\"")
                    .contains("\"jobTitle\":\"백엔드 개발\"")
                    .contains("\"completedRoomCount\":4")
                    .contains("\"attendanceRate\":100")
                    .contains("\"averageRating\":4.7")
                    .contains("\"label\":\"시간을 잘 지켜요\",\"count\":6")
                    .doesNotContain("\"email\"")
                    .doesNotContain("\"socialSubject\"")
                    .doesNotContain("\"termsAgreements\"")
                    .doesNotContain("\"interestCompanyIds\"")
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
    fun `탈퇴한 회원은 공개 정보를 숨기고 탈퇴한 사용자로 표시한다`() {
        mockMvc.perform(get("/v1/members/{memberId}/profile", withdrawnMemberId))
            .andExpect(status().isOk)
            .andExpect { result ->
                assertThat(result.response.contentAsString)
                    .contains("\"withdrawn\":true")
                    .contains("\"nickname\":\"탈퇴한 사용자\"")
                    .contains("\"jobTitle\":null")
                    .contains("\"bio\":null")
                    .contains("\"trust\":null")
                    .contains("\"recentActivities\":[]")
            }
            .andDo(
                documentApi(
                    "publicProfile-withdrawn",
                    publicProfileSummary,
                    publicProfileDescription,
                    publicProfilePathParameters(),
                    publicProfileResponseFields(),
                ),
            )
    }

    @Test
    fun `존재하지 않는 회원의 공개 프로필은 E1006 을 반환한다`() {
        mockMvc.perform(get("/v1/members/{memberId}/profile", unknownMemberId))
            .andExpect(status().isNotFound)
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
    fun `회원 식별자가 UUID 형식이 아니면 E400 을 반환한다`() {
        mockMvc.perform(get("/v1/members/{memberId}/profile", "not-a-uuid"))
            .andExpect(status().isBadRequest)
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

    private fun publicProfilePathParameters() = pathParameters(
        parameterWithName("memberId").description("조회할 회원 식별자 (UUID)"),
    )

    private fun publicProfileResponseFields() = responseFields(
        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
        fieldWithPath("data.withdrawn").type(JsonFieldType.BOOLEAN).description("탈퇴한 회원 여부"),
        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("닉네임 (탈퇴 회원은 '탈퇴한 사용자')"),
        fieldWithPath("data.jobTitle").type(JsonFieldType.STRING).optional().description("직무 (선택, 탈퇴 회원은 null)"),
        fieldWithPath("data.bio").type(JsonFieldType.STRING).optional().description("자기소개 (선택, 탈퇴 회원은 null)"),
        fieldWithPath("data.trust").type(JsonFieldType.OBJECT).optional()
            .description("신뢰 정보 (목에서만 제공, 실 API는 이번 스프린트에 null)"),
        fieldWithPath("data.trust.completedRoomCount").type(JsonFieldType.NUMBER).optional().description("완료한 룸 수"),
        fieldWithPath("data.trust.attendanceRate").type(JsonFieldType.NUMBER).optional().description("출석률 (%)"),
        fieldWithPath("data.trust.averageRating").type(JsonFieldType.NUMBER).optional().description("평균 별점"),
        fieldWithPath("data.trust.representativeTags").type(JsonFieldType.ARRAY).optional().description("대표 평가 태그"),
        fieldWithPath("data.trust.representativeTags[].label").type(JsonFieldType.STRING).optional().description("평가 문구"),
        fieldWithPath("data.trust.representativeTags[].count").type(JsonFieldType.NUMBER).optional().description("받은 횟수"),
        fieldWithPath("data.recentActivities").type(JsonFieldType.ARRAY).description("최근 공개 활동 (없으면 빈 배열)"),
        fieldWithPath("data.recentActivities[].role").type(JsonFieldType.STRING).optional().description("역할 (PARTICIPANT | HOST)"),
        fieldWithPath("data.recentActivities[].title").type(JsonFieldType.STRING).optional().description("활동 제목"),
        fieldWithPath("data.recentActivities[].date").type(JsonFieldType.STRING).optional().description("활동 일자 (yyyy-MM-dd)"),
        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
    )
}
