package io.plady.moimyeon.security.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class PerfAuthenticationFilterTest {
    private val filter = PerfAuthenticationFilter()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `헤더의 회원 UUID로 인증을 세팅한다`() {
        val memberId = UUID.randomUUID()
        val request = MockHttpServletRequest().apply {
            addHeader(PerfAuthenticationFilter.TEST_USER_ID_HEADER, memberId.toString())
        }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        val authentication = SecurityContextHolder.getContext().authentication
        assertThat(authentication?.name).isEqualTo(memberId.toString())
        assertThat(authentication?.authorities?.map { it.authority }).containsExactly("ROLE_USER")
    }

    @Test
    fun `헤더가_없으면_인증을_세팅하지_않는다`() {
        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }

    @Test
    fun `헤더가 UUID 형식이 아니면 인증을 세팅하지 않는다`() {
        val request = MockHttpServletRequest().apply {
            addHeader(PerfAuthenticationFilter.TEST_USER_ID_HEADER, "not-a-uuid")
        }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }
}
