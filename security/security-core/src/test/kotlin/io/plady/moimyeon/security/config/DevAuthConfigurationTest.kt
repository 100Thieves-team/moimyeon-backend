package io.plady.moimyeon.security.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.StandardEnvironment

@Tag("context")
class DevAuthConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withInitializer { context ->
            context.environment.propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
        }
        .withPropertyValues(
            "spring.config.name=security-core",
            "spring.profiles.active=dev",
        )
        .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `dev 인증 설정은 preview 프론트와 세션을 공유하되 운영 세션과 격리한다`() {
        contextRunner.run { context ->
            val properties = context.getBean(AuthProperties::class.java)

            assertThat(properties.oauth2.successRedirectUri.toString())
                .isEqualTo("https://dev.moimyeon.plady.io/auth/callback")
            assertThat(properties.oauth2.failureRedirectUri.toString())
                .isEqualTo("https://dev.moimyeon.plady.io/?authError=login_failed")
            assertThat(properties.cookie.accessTokenName).isEqualTo("DEV_ACCESS_TOKEN")
            assertThat(properties.cookie.refreshTokenName).isEqualTo("DEV_REFRESH_TOKEN")
            assertThat(properties.cookie.domain).isEqualTo("dev.moimyeon.plady.io")
            assertThat(properties.cookie.secure).isTrue()
            assertThat(properties.cookie.sameSite).isEqualTo("None")
            assertThat(properties.cors.allowedOrigins).containsExactly(
                "https://dev.moimyeon.plady.io",
                "http://localhost:3000",
            )
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties::class)
    class TestConfiguration
}
