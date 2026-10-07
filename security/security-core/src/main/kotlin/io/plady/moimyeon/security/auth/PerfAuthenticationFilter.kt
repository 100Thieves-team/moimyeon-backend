package io.plady.moimyeon.security.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

// 부하테스트 전용 인증 필터. 헤더의 회원 UUID를 그대로 신뢰하므로 절대 운영 환경에 활성화되면 안 된다.
// 의도적으로 @Component 를 붙이지 않는다 — PerfAuthConfig(perf 프로파일 + 프로퍼티 이중 게이트)로만 등록된다.
class PerfAuthenticationFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val memberId = request.getHeader(TEST_USER_ID_HEADER)?.toUuidOrNull()
        if (memberId != null) {
            val authentication = UsernamePasswordAuthenticationToken(
                memberId.toString(),
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
            SecurityContextHolder.getContext().authentication = authentication
        }
        filterChain.doFilter(request, response)
    }

    private fun String.toUuidOrNull(): UUID? = try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        const val TEST_USER_ID_HEADER = "X-Test-User-Id"
    }
}
