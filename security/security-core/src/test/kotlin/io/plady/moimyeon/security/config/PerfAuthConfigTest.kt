package io.plady.moimyeon.security.config

import io.plady.moimyeon.security.auth.PerfAuthenticationFilter
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.assertj.core.api.Assertions.assertThat as assertThatValue

@Tag("context")
class PerfAuthConfigTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(PerfAuthConfig::class.java)

    @Test
    fun `perf 프로파일과 활성화 프로퍼티가 모두 있으면 필터를 등록한다`() {
        contextRunner
            .withProfiles("dev", "perf")
            .withPropertyValues("security.perf-auth.enabled=true")
            .run { context -> context.assertSinglePerfAuthenticationFilter() }
    }

    @Test
    fun `perf 프로파일만 있으면 필터를 등록하지 않는다`() {
        contextRunner
            .withProfiles("dev", "perf")
            .run { context -> context.assertNoPerfAuthenticationFilter() }
    }

    @Test
    fun `활성화 프로퍼티만 있으면 필터를 등록하지 않는다`() {
        contextRunner
            .withProfiles("dev")
            .withPropertyValues("security.perf-auth.enabled=true")
            .run { context -> context.assertNoPerfAuthenticationFilter() }
    }

    @Test
    fun `live 프로파일에서는 perf 설정을 함께 넣어도 필터를 등록하지 않는다`() {
        contextRunner
            .withProfiles("live", "perf")
            .withPropertyValues("security.perf-auth.enabled=true")
            .run { context -> context.assertNoPerfAuthenticationFilter() }
    }

    private fun ApplicationContextRunner.withProfiles(vararg profiles: String): ApplicationContextRunner {
        return withInitializer { context -> context.environment.setActiveProfiles(*profiles) }
    }

    private fun AssertableApplicationContext.assertSinglePerfAuthenticationFilter() {
        assertThatValue(this).hasSingleBean(PerfAuthenticationFilter::class.java)
    }

    private fun AssertableApplicationContext.assertNoPerfAuthenticationFilter() {
        assertThatValue(this).doesNotHaveBean(PerfAuthenticationFilter::class.java)
    }
}
