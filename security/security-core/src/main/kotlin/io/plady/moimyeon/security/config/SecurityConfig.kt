package io.plady.moimyeon.security.config

import io.plady.moimyeon.security.auth.PerfAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val perfAuthenticationFilter: PerfAuthenticationFilter? = null,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            authorizeHttpRequests {
                authorize("/oauth2/**", permitAll)
                authorize("/login/**", permitAll)

                // TODO: 인증/인가 정책 확정 후 경로별 규칙 추가
                authorize(anyRequest, permitAll)
            }
            oauth2Login {  }

            if (perfAuthenticationFilter != null) {
                addFilterBefore<UsernamePasswordAuthenticationFilter>(perfAuthenticationFilter)
            }
        }
        return http.build()
    }
}
