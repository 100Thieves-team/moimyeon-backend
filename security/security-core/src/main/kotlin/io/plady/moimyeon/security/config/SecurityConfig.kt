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
    // core-api 가 SocialMemberResolver 어댑터를 제공해야 이 핸들러 빈이 뜬다 → 없어도 로딩되게 옵션 주입
    private val oauth2LoginSuccessHandler: OAuth2LoginSuccessHandler? = null,
    // 필터 레벨 401/403 을 공통 ApiResponse 포맷으로. 구현은 core-api 가 제공(AuthErrorWriter 어댑터) → 옵션 주입
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

                // TODO: 인증/인가 정책 확정 후 경로별 규칙 추가
                authorize(anyRequest, permitAll)
            }
            oauth2Login {
                oauth2LoginSuccessHandler?.let { authenticationSuccessHandler = it }
            }
            oauth2ResourceServer {
                // 웹은 쿠키, 앱은 Authorization 헤더로 토큰 전달 → 둘 다 수용
                bearerTokenResolver = HeaderOrCookieBearerTokenResolver()
                apiAuthenticationEntryPoint?.let { authenticationEntryPoint = it } // 토큰 검증 실패도 공통 포맷
                jwt { } // JwtConfig.jwtDecoder(HMAC) 사용, sub → Authentication.name(memberId UUID)
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
