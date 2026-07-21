package io.plady.moimyeon.security.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class PerfAuthenticationFilterTest {
    private val filter = PerfAuthenticationFilter()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `헤더의_userId로_인증을_세팅한다`() {
        val request = MockHttpServletRequest().apply {
            addHeader(PerfAuthenticationFilter.TEST_USER_ID_HEADER, "42")
        }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        val authentication = SecurityContextHolder.getContext().authentication
        assertThat(authentication?.name).isEqualTo("42")
        assertThat(authentication?.authorities?.map { it.authority }).containsExactly("ROLE_USER")
    }

    @Test
    fun `헤더가_없으면_인증을_세팅하지_않는다`() {
        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `헤더가_userId_형식이_아니면_인증을_세팅하지_않는다`() {
        val request = MockHttpServletRequest().apply {
            addHeader(PerfAuthenticationFilter.TEST_USER_ID_HEADER, "not-a-number")
        }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }
}
