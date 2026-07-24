package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
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
}
