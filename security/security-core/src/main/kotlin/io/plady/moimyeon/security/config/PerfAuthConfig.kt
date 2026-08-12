package io.plady.moimyeon.security.config

import io.plady.moimyeon.security.auth.PerfAuthenticationFilter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// perf 프로파일 + 명시적 프로퍼티 이중 게이트. live 프로파일에서는 어떤 조합으로도 등록하지 않는다.
@Configuration
@Profile("perf & !live")
@ConditionalOnProperty(prefix = "security.perf-auth", name = ["enabled"], havingValue = "true")
class PerfAuthConfig {
    @Bean
    fun perfAuthenticationFilter(): PerfAuthenticationFilter {
        return PerfAuthenticationFilter()
    }
}
