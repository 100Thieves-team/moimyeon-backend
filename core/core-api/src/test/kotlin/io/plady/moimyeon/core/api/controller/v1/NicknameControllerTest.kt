package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class NicknameControllerTest : RestDocsTest() {
    @BeforeEach
    fun setUp() {
        mockMvc = mockController(NicknameController())
    }

    @Test
    fun nicknameSuggestion() {
        mockMvc.perform(get("/v1/nicknames/suggestion"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "nicknameSuggestion",
                    "닉네임 자동 추천",
                    "중복되지 않는 닉네임을 새로 생성해 반환한다. 최초 프로필 모달의 프리필과 ↻ 새로 만들기 재생성에서 사용한다. " +
                        "(모킹: 항상 '명랑한 알파카 42' 고정 반환)",
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).description("추천 닉네임 (중복 아님 보장)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun nicknameAvailability() {
        mockMvc.perform(get("/v1/nicknames/availability").param("value", "차분한 펭귄 12"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "nicknameAvailability",
                    "닉네임 사용 가능 여부 확인",
                    "닉네임의 전체 중복 여부를 확인한다. 형식 위반(길이·문자)은 available=false 가 아니라 400(E1005)으로 응답한다. " +
                        "(모킹: '집요한 수달 07' 만 false, 나머지는 항상 true)",
                    queryParameters(
                        parameterWithName("value").description("확인할 닉네임"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.available").type(JsonFieldType.BOOLEAN).description("사용 가능 여부 (중복이면 false)"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
