package io.plady.moimyeon.worker.notification

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.env.PropertySourcesPropertyResolver
import org.springframework.core.io.ClassPathResource

class NotificationActionBaseUrlConfigTest {
    private val documents: List<PropertySource<*>> =
        YamlPropertySourceLoader().load("application.yml", ClassPathResource("application.yml"))

    @Test
    fun `프로필마다 자기 환경의 프론트 주소를 쓴다`() {
        assertThat(actionBaseUrlOf("local")).isEqualTo("http://localhost:3000")
        assertThat(actionBaseUrlOf("local-dev")).isEqualTo("http://localhost:3000")
        assertThat(actionBaseUrlOf("dev")).isEqualTo("https://dev.moimyeon.plady.io")
        assertThat(actionBaseUrlOf("live")).isEqualTo("https://moimyeon.plady.io")
    }

    @Test
    fun `주소를 정하지 않은 프로필은 환경 변수 없이 주소를 풀 수 없다`() {
        assertThatThrownBy { actionBaseUrlOf("staging") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    // 시스템 환경 변수를 넣지 않아 프로필 기본값이 드러나게 한다.
    private fun actionBaseUrlOf(profile: String): String {
        val sources = MutablePropertySources()
        documents.filter { it.getProperty(ACTIVATE_ON_PROFILE) == profile }.forEach(sources::addLast)
        documents.filter { it.getProperty(ACTIVATE_ON_PROFILE) == null }.forEach(sources::addLast)
        return PropertySourcesPropertyResolver(sources).getRequiredProperty(ACTION_BASE_URL)
    }

    private companion object {
        const val ACTIVATE_ON_PROFILE = "spring.config.activate.on-profile"
        const val ACTION_BASE_URL = "notification.action-base-url"
    }
}
