package io.plady.moimyeon.security.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

// 프론트가 쿠키를 실어 api. 를 호출하므로 자격증명 허용 CORS. 오리진은 프로파일별 설정(security.auth.cors)에서.
@Configuration
class CorsConfig(
    private val authProperties: AuthProperties,
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = authProperties.cors.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true // allowCredentials=true 이면 allowedOrigins 에 "*" 금지 → 명시적 오리진만
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
