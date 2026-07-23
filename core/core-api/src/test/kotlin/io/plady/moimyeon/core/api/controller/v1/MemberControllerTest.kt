package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.plady.moimyeon.core.api.controller.v1.request.CreateProfileRequest
import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.enums.MeetingPreference
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.security.Principal
import java.util.UUID

class MemberControllerTest : RestDocsTest() {
    private val memberId: UUID = UUID.randomUUID()
    private val principal = Principal { memberId.toString() }

    @BeforeEach
    fun setUp() {
        mockMvc = mockController(MemberController(), LoginMemberArgumentResolver())
    }

    @Test
    fun memberMe() {
        mockMvc.perform(get("/v1/members/me").principal(principal))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "memberMe",
                    "내 상태 조회",
                    "인증된 회원의 상태와 프로필 완성 여부를 반환한다. 프로필 미작성이면 profileCompleted=false, profile=null 이다. " +
                        "profile 은 필수 프로필 작성 응답의 data 와 동일한 모양이다. " +
                        "FE 는 로그인 직후 이 값으로 최초 프로필 작성 모달 노출 여부를 판단한다. (모킹: 항상 미작성 상태로 응답)",
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
                        fieldWithPath("data.email").type(JsonFieldType.STRING).description("대표 이메일"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("회원 상태 (ACTIVE | RESTRICTED | WITHDRAWN)"),
                        fieldWithPath("data.profileCompleted").type(JsonFieldType.BOOLEAN).description("필수 프로필 작성 완료 여부"),
                        fieldWithPath("data.profile").type(JsonFieldType.NULL).optional()
                            .description("프로필 (미작성이면 null, 작성 완료 시 필수 프로필 작성 응답의 data 와 동일 모양)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun createProfile() {
        val request = CreateProfileRequest(
            nickname = "꼼꼼한 라쿤 34",
            jobTitle = "백엔드 개발",
            bio = "실전처럼 압박 질문을 주고받는 걸 좋아해요.",
            meetingPreference = MeetingPreference.BOTH,
            region = "서울 · 마포구",
        )

        mockMvc.perform(
            post("/v1/members/me/profile")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "createProfile",
                    "필수 프로필 작성",
                    "닉네임(필수)과 선택 정보를 받아 프로필을 생성하고 프로필 완성 상태로 전환한다. " +
                        "닉네임 형식 위반은 400(E1005), 중복은 409(E1007), 이미 작성된 경우 409(E1008)로 응답한다. " +
                        "(모킹: 저장 없이 요청을 그대로 반환하며, 중복 E1007 은 닉네임 '집요한 수달 07' 로 재현할 수 있다)",
                    requestFields(
                        fieldWithPath("nickname").type(JsonFieldType.STRING).description("닉네임 (필수, 전체 중복 불가)"),
                        fieldWithPath("jobTitle").type(JsonFieldType.STRING).optional().description("직무 (선택)"),
                        fieldWithPath("bio").type(JsonFieldType.STRING).optional().description("자기소개 (선택)"),
                        fieldWithPath("meetingPreference").type(JsonFieldType.STRING).optional()
                            .description("진행 방식 선호 (ONLINE | OFFLINE | BOTH, 선택)"),
                        fieldWithPath("region").type(JsonFieldType.STRING).optional().description("선호 지역 (선택)"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.memberId").type(JsonFieldType.STRING).description("회원 식별자 (UUID)"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("닉네임"),
                        fieldWithPath("data.jobTitle").type(JsonFieldType.STRING).optional().description("직무 (선택)"),
                        fieldWithPath("data.bio").type(JsonFieldType.STRING).optional().description("자기소개 (선택)"),
                        fieldWithPath("data.meetingPreference").type(JsonFieldType.STRING).optional()
                            .description("진행 방식 선호 (ONLINE | OFFLINE | BOTH, 선택)"),
                        fieldWithPath("data.region").type(JsonFieldType.STRING).optional().description("선호 지역 (선택)"),
                        fieldWithPath("data.profileCompleted").type(JsonFieldType.BOOLEAN).description("필수 프로필 작성 완료 여부"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
