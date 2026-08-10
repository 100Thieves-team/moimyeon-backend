package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.security.auth.JwtTokenProvider
import jakarta.servlet.Filter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

@Import(AdminAuthorizationContextTest.TestConfig::class)
class AdminAuthorizationContextTest(
    private val webApplicationContext: WebApplicationContext,
    @Qualifier("springSecurityFilterChain") private val securityFilterChain: Filter,
    private val jwtTokenProvider: JwtTokenProvider,
) : ContextTest() {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val builder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(securityFilterChain)
        mockMvc = builder.build()
    }

    @Test
    fun `미인증 회원은 관리자 경로에 접근할 수 없다`() {
        val response = mockMvc.perform(get("/admin/security-test")).andReturn().response

        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `일반 회원은 관리자 경로에 접근할 수 없다`() {
        val accessToken = jwtTokenProvider.issue(UUID.randomUUID(), MemberRole.USER)

        val response = mockMvc.perform(
            get("/admin/security-test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        ).andReturn().response

        assertThat(response.status).isEqualTo(403)
    }

    @Test
    fun `관리자는 관리자 경로에 접근할 수 있다`() {
        val accessToken = jwtTokenProvider.issue(UUID.randomUUID(), MemberRole.ADMIN)

        val response = mockMvc.perform(
            get("/admin/security-test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        ).andReturn().response

        assertThat(response.status).isEqualTo(200)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun adminAuthorizationTestController() = AdminAuthorizationTestController()
    }

    @RestController
    class AdminAuthorizationTestController {
        @GetMapping("/admin/security-test")
        fun get(): String = "ok"
    }
}
