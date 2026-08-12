package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.api.security.CurrentMember
import io.plady.moimyeon.core.api.security.LoginMember
import io.plady.moimyeon.security.auth.PerfAuthenticationFilter
import jakarta.servlet.Filter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

@ActiveProfiles(profiles = ["test", "perf"], inheritProfiles = false)
@TestPropertySource(properties = ["security.perf-auth.enabled=true"])
@Import(PerfAuthenticationContextTest.TestConfig::class)
class PerfAuthenticationContextTest(
    private val webApplicationContext: WebApplicationContext,
    @Qualifier("springSecurityFilterChain") private val securityFilterChain: Filter,
) : ContextTest() {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val builder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(securityFilterChain)
        mockMvc = builder.build()
    }

    @Test
    fun `perf 환경의 UUID 헤더는 로그인 회원으로 전달된다`() {
        val memberId = UUID.randomUUID()

        val response = mockMvc.perform(
            get("/perf-auth-test")
                .header(PerfAuthenticationFilter.TEST_USER_ID_HEADER, memberId.toString()),
        ).andReturn().response

        assertThat(response.status).isEqualTo(200)
        assertThat(response.contentAsString).isEqualTo(memberId.toString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {
        @Bean
        fun perfAuthenticationTestController() = PerfAuthenticationTestController()
    }

    @RestController
    class PerfAuthenticationTestController {
        @GetMapping("/perf-auth-test")
        fun get(
            @LoginMember currentMember: CurrentMember,
        ): String = currentMember.id.toString()
    }
}
