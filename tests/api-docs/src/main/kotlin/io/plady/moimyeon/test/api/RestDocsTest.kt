package io.plady.moimyeon.test.api

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper
import jakarta.servlet.Filter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.operation.preprocess.Preprocessors
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.payload.ResponseFieldsSnippet
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.method.support.HandlerMethodArgumentResolver

@Tag("restdocs")
@ExtendWith(RestDocumentationExtension::class)
abstract class RestDocsTest {
    protected lateinit var mockMvc: MockMvc
    private lateinit var restDocumentation: RestDocumentationContextProvider

    @BeforeEach
    fun setUp(restDocumentation: RestDocumentationContextProvider) {
        this.restDocumentation = restDocumentation
    }

    protected fun mockController(
        controller: Any,
        vararg argumentResolvers: HandlerMethodArgumentResolver,
        controllerAdvice: Any? = null,
        filters: List<Filter> = emptyList(),
    ): MockMvc {
        val builder = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(*argumentResolvers)
            .apply<StandaloneMockMvcBuilder>(MockMvcRestDocumentation.documentationConfiguration(restDocumentation))
        controllerAdvice?.let { builder.setControllerAdvice(it) }
        if (filters.isNotEmpty()) {
            builder.addFilters<StandaloneMockMvcBuilder>(*filters.toTypedArray())
        }
        return builder.build()
    }

    protected fun documentApi(
        identifier: String,
        summary: String,
        description: String,
        vararg snippets: Snippet,
    ) = MockMvcRestDocumentationWrapper.document(
        identifier = identifier,
        summary = summary,
        description = description,
        requestPreprocessor = Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
        responsePreprocessor = Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
        snippets = snippets,
    )

    protected fun documentDeprecatedApi(
        identifier: String,
        summary: String,
        description: String,
        vararg snippets: Snippet,
    ) = MockMvcRestDocumentationWrapper.document(
        identifier = identifier,
        summary = summary,
        description = description,
        deprecated = true,
        requestPreprocessor = Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
        responsePreprocessor = Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
        snippets = snippets,
    )

    // 모든 에러 응답이 공유하는 봉투. 예외 케이스 문서화 테스트에서 사용한다.
    protected fun errorResponseFields(): ResponseFieldsSnippet = responseFields(
        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (ERROR)"),
        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
        fieldWithPath("error.code").type(JsonFieldType.STRING).description("에러 코드 (E 코드, 분기 기준)"),
        fieldWithPath("error.message").type(JsonFieldType.STRING).description("사용자에게 표시 가능한 메시지"),
        // 타입을 명시하지 않으면 예시 페이로드(null/object)에서 추론되어 OpenAPI 3.0 에 유효하지 않은
        // oneOf: [null, object] 스키마가 생성된다. OBJECT 로 고정한다(nullable 은 optional 에서 나온다).
        subsectionWithPath("error.data").type(JsonFieldType.OBJECT).optional()
            .description("추가 정보 (검증 오류의 필드별 사유 등, 없으면 null)"),
    )

    protected fun successResponseFields(vararg dataFields: FieldDescriptor): ResponseFieldsSnippet {
        return responseFields(
            fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
            *dataFields,
            fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
        )
    }

    protected fun emptySuccessResponseFields(): ResponseFieldsSnippet = responseFields(
        fieldWithPath("result").type(JsonFieldType.STRING).description("처리 결과 (SUCCESS)"),
        fieldWithPath("data").type(JsonFieldType.NULL).ignored(),
        fieldWithPath("error").type(JsonFieldType.NULL).ignored(),
    )
}
