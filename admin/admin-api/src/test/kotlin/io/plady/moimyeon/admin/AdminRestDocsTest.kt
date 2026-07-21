package io.plady.moimyeon.admin

import io.plady.moimyeon.admin.config.AdminProviderArgumentResolver
import io.plady.moimyeon.test.api.RestDocsTest
import org.junit.jupiter.api.BeforeEach
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder

abstract class AdminRestDocsTest : RestDocsTest() {
    private lateinit var adminRestDocumentation: RestDocumentationContextProvider

    @BeforeEach
    fun setUpAdminRestDocs(restDocumentation: RestDocumentationContextProvider) {
        this.adminRestDocumentation = restDocumentation
    }

    // AdminProvider 파라미터를 쓰는 컨트롤러를 standalone 으로 띄울 때 사용한다
    protected fun mockAdminController(controller: Any): MockMvc {
        return MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(AdminProviderArgumentResolver())
            .apply<StandaloneMockMvcBuilder>(MockMvcRestDocumentation.documentationConfiguration(adminRestDocumentation))
            .build()
    }
}
