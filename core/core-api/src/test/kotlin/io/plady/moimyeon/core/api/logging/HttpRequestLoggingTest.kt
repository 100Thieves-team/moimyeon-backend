package io.plady.moimyeon.core.api.logging

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.support.logging.RequestLogEntry
import io.plady.moimyeon.support.logging.RequestLogWriter
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequestEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class HttpRequestLoggingTest {
    @AfterEach
    fun clearContext() = MDC.clear()

    @Test
    fun `MDC를 복원하고 완료 시 한 번만 기록하며 클라이언트 요청 ID는 신뢰하지 않는다`() {
        var time = 0L
        val filter = HttpRequestLoggingFilter { time }
        val writer = mockk<RequestLogWriter>(relaxed = true)
        val listener = HttpRequestLogCompletionListener(writer) { time }
        val request = MockHttpServletRequest("GET", "/private@example.invalid").apply {
            addHeader("X-Request-Id", "client-supplied")
        }
        val response = MockHttpServletResponse()
        val captured = slot<RequestLogEntry>()
        MDC.put("requestId", "outer-request")
        MDC.put("custom", "outer-value")
        MDC.put("traceId", "0123456789abcdef0123456789abcdef")

        filter.doFilter(request, response) { _, _ ->
            assertThat(MDC.get("requestId")).isNotEqualTo("outer-request").isNotEqualTo("client-supplied")
            time = 42_000_000L
            response.status = 204
        }
        assertThat(MDC.get("requestId")).isEqualTo("outer-request")
        assertThat(MDC.get("custom")).isEqualTo("outer-value")
        verify(exactly = 0) { writer.write(any()) }

        MDC.put("traceId", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        every { writer.write(any()) } answers {
            assertThat(MDC.get("traceId")).isEqualTo("0123456789abcdef0123456789abcdef")
            Unit
        }

        listener.requestDestroyed(ServletRequestEvent(request.servletContext, request))
        listener.requestDestroyed(ServletRequestEvent(request.servletContext, request))

        verify(exactly = 1) { writer.write(capture(captured)) }
        assertThat(captured.captured.routeTemplate).isEqualTo("UNMATCHED")
        assertThat(captured.captured.status).isEqualTo(204)
        assertThat(captured.captured.durationMs).isEqualTo(42)
        assertThat(captured.captured.requestId).isNotBlank()
        assertThat(MDC.get("requestId")).isEqualTo("outer-request")
        assertThat(MDC.get("traceId")).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    }

    @Test
    fun `필터 예외 이후에도 MDC는 복원하며 컨테이너가 정한 최종 상태를 기록한다`() {
        val filter = HttpRequestLoggingFilter()
        val writer = mockk<RequestLogWriter>(relaxed = true)
        val request = MockHttpServletRequest("GET", "/failure")
        val response = MockHttpServletResponse()
        MDC.put("requestId", "outer-request")
        assertThatThrownBy {
            filter.doFilter(request, response) { _, _ -> throw ServletException("private") }
        }.isInstanceOf(ServletException::class.java)
        assertThat(MDC.get("requestId")).isEqualTo("outer-request")
        verify(exactly = 0) { writer.write(any()) }
        response.status = 500

        HttpRequestLogCompletionListener(writer).requestDestroyed(ServletRequestEvent(request.servletContext, request))

        verify { writer.write(match { it.status == 500 }) }
    }

    @Test
    fun `로그 기록이 실패해도 응답과 완료 스레드의 MDC를 보존한다`() {
        val writer = mockk<RequestLogWriter>()
        every { writer.write(any()) } throws IllegalStateException("private")
        val request = MockHttpServletRequest("GET", "/anything")
        val response = MockHttpServletResponse()
        HttpRequestLoggingFilter().doFilter(request, response) { _, _ ->
            response.status = 201
            response.writer.write("unchanged")
        }
        MDC.put("requestId", "another-request")

        HttpRequestLogCompletionListener(writer).requestDestroyed(ServletRequestEvent(request.servletContext, request))

        assertThat(response.status).isEqualTo(201)
        assertThat(response.contentAsString).isEqualTo("unchanged")
        assertThat(MDC.get("requestId")).isEqualTo("another-request")
    }
}
