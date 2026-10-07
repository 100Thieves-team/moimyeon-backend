package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.security.auth.OAuth2LoginFailureHandler
import io.plady.moimyeon.security.auth.OAuth2LoginSuccessHandler
import io.plady.moimyeon.security.config.AuthProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.util.ReflectionTestUtils

class SecurityFilterChainContextTest(
    @Qualifier("springSecurityFilterChain") private val filterChainProxy: FilterChainProxy,
    private val oauth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val oauth2LoginFailureHandler: OAuth2LoginFailureHandler,
    private val authProperties: AuthProperties,
) : ContextTest() {
    @Test
    fun `OAuth 로그인 필터는 자체 성공 핸들러로 세션을 발급한다`() {
        val oauth2LoginFilter = requireNotNull(filterChainProxy.getFilters("/login/oauth2/code/google"))
            .single { it.javaClass.simpleName == "OAuth2LoginAuthenticationFilter" }

        assertThat(ReflectionTestUtils.getField(oauth2LoginFilter, "successHandler"))
            .isSameAs(oauth2LoginSuccessHandler)
        assertThat(ReflectionTestUtils.getField(oauth2LoginFilter, "failureHandler"))
            .isSameAs(oauth2LoginFailureHandler)
    }

    @Test
    fun `Google 로그인 시작 경로는 실제 필터 체인에서 인가 화면으로 이동한다`() {
        val request = MockHttpServletRequest("GET", "/oauth2/authorization/google").apply {
            servletPath = "/oauth2/authorization/google"
        }
        val response = MockHttpServletResponse()

        filterChainProxy.doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).startsWith("https://accounts.google.com/o/oauth2/v2/auth")
    }

    @Test
    fun `Google 실패 콜백은 실제 필터 체인에서 프론트 실패 화면으로 이동한다`() {
        val request = MockHttpServletRequest("GET", "/login/oauth2/code/google").apply {
            servletPath = "/login/oauth2/code/google"
            addParameter("error", "access_denied")
        }
        val response = MockHttpServletResponse()

        filterChainProxy.doFilter(request, response, MockFilterChain())

        assertThat(response.status).isEqualTo(302)
        assertThat(response.redirectedUrl).isEqualTo(authProperties.oauth2.failureRedirectUri.toString())
    }

    @Test
    fun `일반 환경에는 부하테스트 인증 필터가 없다`() {
        assertThat(requireNotNull(filterChainProxy.getFilters("/v1/members/me")))
            .noneMatch { it.javaClass.simpleName == "PerfAuthenticationFilter" }
    }
}
