package io.plady.moimyeon.core.api.controller.v1

import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.v1.request.DoExampleRequest
import io.plady.moimyeon.core.domain.ExampleResult
import io.plady.moimyeon.core.domain.ExampleService
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
import org.springframework.restdocs.request.RequestDocumentation
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ExampleControllerTest : RestDocsTest() {
    private lateinit var exampleService: ExampleService

    @BeforeEach
    fun setUp() {
        exampleService = mockk()
        mockMvc = mockController(ExampleController(exampleService))
    }

    @Test
    fun exampleGet() {
        every { exampleService.processExample(any()) } returns ExampleResult("BYE_GET")

        mockMvc.perform(
            get("/get/{exampleValue}", "HELLO_PATH")
                .param("exampleParam", "HELLO_PARAM")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "exampleGet",
                    "예제 조회",
                    "경로/쿼리 파라미터를 받아 예제 데이터를 조회한다.",
                    RequestDocumentation.pathParameters(
                        parameterWithName("exampleValue").description("ExampleValue"),
                    ),
                    queryParameters(
                        parameterWithName("exampleParam").description("ExampleParam"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("ResultType"),
                        fieldWithPath("data.result").type(JsonFieldType.STRING).description("Result Data"),
                        fieldWithPath("data.date").type(JsonFieldType.STRING).description("Result Date"),
                        fieldWithPath("data.datetime").type(JsonFieldType.STRING).description("Result Datetime"),
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("Result Items"),
                        fieldWithPath("data.items[].key").type(JsonFieldType.STRING).description("Result Item"),
                        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
                    ),
                ),
            )
    }

    @Test
    fun examplePost() {
        every { exampleService.processExample(any()) } returns ExampleResult("BYE_POST")

        mockMvc.perform(
            post("/post")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper().writeValueAsString(DoExampleRequest("HELLO_BODY"))),
        )
            .andExpect(status().isOk)
            .andDo(
                documentApi(
                    "examplePost",
                    "예제 등록",
                    "예제 데이터를 받아 처리 결과를 반환한다.",
                    requestFields(
                        fieldWithPath("data").type(JsonFieldType.STRING).description("ExampleBody Data Field"),
                    ),
                    responseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("ResultType"),
                        fieldWithPath("data.result").type(JsonFieldType.STRING).description("Result Data"),
                        fieldWithPath("data.date").type(JsonFieldType.STRING).description("Result Date"),
                        fieldWithPath("data.datetime").type(JsonFieldType.STRING).description("Result Datetime"),
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("Result Items"),
                        fieldWithPath("data.items[].key").type(JsonFieldType.STRING).description("Result Item"),
                        fieldWithPath("error").type(JsonFieldType.STRING).ignored(),
                    ),
                ),
            )
    }
}
