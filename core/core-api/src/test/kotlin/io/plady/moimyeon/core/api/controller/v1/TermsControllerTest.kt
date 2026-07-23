package io.plady.moimyeon.core.api.controller.v1

import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class TermsControllerTest : RestDocsTest() {
    @BeforeEach
    fun setUp() {
        mockMvc = mockController(TermsController())
    }

    @Test
    fun termsList() {
        mockMvc.perform(get("/v1/terms"))
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "termsList",
                    "현재 유효 약관 목록 조회",
                    "현재 유효한(ACTIVE) 약관 전체를 본문 포함으로 반환한다. 로그인 모달의 약관 링크에서 사용하므로 비로그인 접근을 허용한다.",
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
                        fieldWithPath("data.terms").type(JsonFieldType.ARRAY).description("현재 유효한 약관 목록"),
                        fieldWithPath("data.terms[].termsId").type(JsonFieldType.STRING).description("약관 버전 식별자 (UUID)"),
                        fieldWithPath("data.terms[].type").type(JsonFieldType.STRING).description("약관 종류 (SERVICE | PRIVACY)"),
                        fieldWithPath("data.terms[].version").type(JsonFieldType.STRING).description("약관 버전"),
                        fieldWithPath("data.terms[].title").type(JsonFieldType.STRING).description("약관 제목"),
                        fieldWithPath("data.terms[].content").type(JsonFieldType.STRING).description("약관 본문"),
                        fieldWithPath("data.terms[].required").type(JsonFieldType.BOOLEAN).description("가입 필수 동의 여부"),
                        fieldWithPath("data.terms[].effectiveFrom").type(JsonFieldType.STRING).description("시행일"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }
}
