package io.plady.moimyeon.test.api

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.operation.preprocess.Preprocessors
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

    protected fun mockController(controller: Any, vararg argumentResolvers: HandlerMethodArgumentResolver): MockMvc {
        return MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(*argumentResolvers)
            .apply<StandaloneMockMvcBuilder>(MockMvcRestDocumentation.documentationConfiguration(restDocumentation))
            .build()
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
}
