package io.plady.moimyeon.security.config

import io.plady.moimyeon.security.auth.HeaderOrCookieBearerTokenResolver
import io.plady.moimyeon.security.auth.OAuth2LoginSuccessHandler
import io.plady.moimyeon.security.auth.PerfAuthenticationFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableConfigurationProperties(AuthProperties::class)
class SecurityConfig(
    private val perfAuthenticationFilter: PerfAuthenticationFilter? = null,
    private val oauth2LoginSuccessHandler: OAuth2LoginSuccessHandler? = null,
    private val apiAuthenticationEntryPoint: AuthenticationEntryPoint? = null,
    private val apiAccessDeniedHandler: AccessDeniedHandler? = null,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            cors { } // CorsConfig.corsConfigurationSource 빈 사용
            authorizeHttpRequests {
                authorize("/oauth2/**", permitAll)
                authorize("/login/**", permitAll)
                authorize("/v1/auth/refresh", permitAll)
                authorize("/v1/auth/logout", permitAll)

                // TODO: 인증/인가 정책 확정 후 경로별 규칙 추가
                authorize(anyRequest, permitAll)
            }
            // 자체 /v1/auth/logout 을 쓰므로 스프링 기본 /logout 필터는 비활성화(혼동 방지)
            logout { disable() }
            oauth2Login {
                oauth2LoginSuccessHandler?.let { authenticationSuccessHandler = it }
            }
            oauth2ResourceServer {
                // 웹은 쿠키, 앱은 Authorization 헤더로 토큰 전달 → 둘 다 수용
                bearerTokenResolver = HeaderOrCookieBearerTokenResolver()
                apiAuthenticationEntryPoint?.let { authenticationEntryPoint = it }
                jwt { }
            }
            exceptionHandling {
                apiAuthenticationEntryPoint?.let { authenticationEntryPoint = it }
                apiAccessDeniedHandler?.let { accessDeniedHandler = it }
            }

            if (perfAuthenticationFilter != null) {
                addFilterBefore<UsernamePasswordAuthenticationFilter>(perfAuthenticationFilter)
            }
        }
        return http.build()
    }
}
