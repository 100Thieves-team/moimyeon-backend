package io.plady.moimyeon.security.config

import io.plady.moimyeon.security.auth.ApiResponseAccessDeniedHandler
import io.plady.moimyeon.security.auth.ApiResponseAuthenticationEntryPoint
import io.plady.moimyeon.security.auth.HeaderOrCookieBearerTokenResolver
import io.plady.moimyeon.security.auth.MemberRoleJwtGrantedAuthoritiesConverter
import io.plady.moimyeon.security.auth.OAuth2LoginFailureHandler
import io.plady.moimyeon.security.auth.OAuth2LoginSuccessHandler
import io.plady.moimyeon.security.auth.PerfAuthenticationFilter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableConfigurationProperties(AuthProperties::class)
class SecurityConfig(
    private val oauth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val oauth2LoginFailureHandler: OAuth2LoginFailureHandler,
    private val apiAuthenticationEntryPoint: ApiResponseAuthenticationEntryPoint,
    private val apiAccessDeniedHandler: ApiResponseAccessDeniedHandler,
    // 부하테스트 우회 필터만 환경에 따라 없을 수 있다. 공통 인증 컴포넌트는 항상 필수로 조립한다.
    private val perfAuthenticationFilterProvider: ObjectProvider<PerfAuthenticationFilter>,
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
                authorize("/health", permitAll)
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize(HttpMethod.GET, "/v1/terms", permitAll)
                authorize(HttpMethod.GET, "/v1/rooms", permitAll)
                authorize(HttpMethod.GET, "/v1/rooms/*", permitAll)
                authorize(HttpMethod.GET, "/v1/job-postings/search", permitAll)
                authorize(HttpMethod.GET, "/v1/companies", permitAll)
                authorize(HttpMethod.GET, "/v1/companies/*/job-postings", permitAll)
                authorize(HttpMethod.GET, "/v1/job-roles", permitAll)
                authorize(HttpMethod.GET, "/v1/job-roles/search", permitAll)
                authorize(HttpMethod.GET, "/v1/regions", permitAll)
                authorize(HttpMethod.GET, "/v1/members/*/profile", permitAll)
                authorize("/admin/**", hasRole("ADMIN"))
                authorize("/actuator/**", denyAll)
                authorize(anyRequest, authenticated)
            }
            // 자체 /v1/auth/logout 을 쓰므로 스프링 기본 /logout 필터는 비활성화(혼동 방지)
            logout { disable() }
            oauth2Login {
                authenticationSuccessHandler = oauth2LoginSuccessHandler
                authenticationFailureHandler = oauth2LoginFailureHandler
            }
            oauth2ResourceServer {
                // 웹은 쿠키, 앱은 Authorization 헤더로 토큰 전달 → 둘 다 수용.
                // refresh/logout 은 만료 AT 로 401 되면 안 되므로 이 경로에선 AT 를 해석하지 않는다.
                bearerTokenResolver = HeaderOrCookieBearerTokenResolver(
                    bearerFreePaths = setOf("/v1/auth/refresh", "/v1/auth/logout"),
                )
                authenticationEntryPoint = apiAuthenticationEntryPoint
                jwt {
                    jwtAuthenticationConverter = JwtAuthenticationConverter().apply {
                        setJwtGrantedAuthoritiesConverter(MemberRoleJwtGrantedAuthoritiesConverter())
                    }
                }
            }
            exceptionHandling {
                authenticationEntryPoint = apiAuthenticationEntryPoint
                accessDeniedHandler = apiAccessDeniedHandler
            }

            perfAuthenticationFilterProvider.getIfAvailable()?.let { perfAuthenticationFilter ->
                addFilterBefore<UsernamePasswordAuthenticationFilter>(perfAuthenticationFilter)
            }
        }
        return http.build()
    }
}
