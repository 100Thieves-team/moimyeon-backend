package io.plady.moimyeon.core.api.controller.v1

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
import java.util.UUID

class PublicProfileControllerTest : RestDocsTest() {
    @BeforeEach
    fun setUp() {
        mockMvc = mockController(PublicProfileController())
    }

    @Test
    fun publicProfile() {
        mockMvc.perform(get("/v1/members/{memberId}/profile", UUID.randomUUID()))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "publicProfile",
                    "공개 프로필 조회",
                    "다른 사용자가 보는 공개 프로필. 공개 범위(닉네임·직무·자기소개)와 신뢰 지표·최근 활동만 노출하고, " +
                        "이메일·비공개 관심 정보는 담지 않는다. 마이페이지의 신뢰 지표 카드도 이 API(자신의 memberId)로 조회한다. " +
                        "(모킹: 세션·피드백 도메인 구현 전까지 figma 목업 값 고정 반환)",
                    pathParameters(
                        parameterWithName("memberId").description("조회할 회원 식별자 (UUID)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("닉네임"),
                        fieldWithPath("data.jobTitle").type(JsonFieldType.STRING).optional().description("직무 (선택)"),
                        fieldWithPath("data.bio").type(JsonFieldType.STRING).optional().description("자기소개 (선택)"),
                        fieldWithPath("data.stats.completedInterviewCount").type(JsonFieldType.NUMBER).description("완료한 면접 수"),
                        fieldWithPath("data.stats.attendanceRate").type(JsonFieldType.NUMBER).description("출석률 (%)"),
                        fieldWithPath("data.stats.noShowCount").type(JsonFieldType.NUMBER).description("노쇼 횟수"),
                        fieldWithPath("data.stats.averageRating").type(JsonFieldType.NUMBER).description("평균 별점"),
                        fieldWithPath("data.frequentReviews").type(JsonFieldType.ARRAY).description("자주 받은 평가 태그"),
                        fieldWithPath("data.frequentReviews[].label").type(JsonFieldType.STRING).description("평가 문구"),
                        fieldWithPath("data.frequentReviews[].count").type(JsonFieldType.NUMBER).description("받은 횟수"),
                        fieldWithPath("data.recentActivities").type(JsonFieldType.ARRAY).description("최근 공개 활동"),
                        fieldWithPath("data.recentActivities[].role").type(JsonFieldType.STRING).description("역할 (PARTICIPANT | HOST)"),
                        fieldWithPath("data.recentActivities[].title").type(JsonFieldType.STRING).description("활동 제목"),
                        fieldWithPath("data.recentActivities[].date").type(JsonFieldType.STRING).description("활동 일자"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
