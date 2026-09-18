package io.plady.moimyeon.storage.db

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

@Tag("context")
class LocalDataConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.config.name=db-core")
        .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `local 프로파일은 참조 데이터와 사람용 로컬 데이터를 함께 로드한다`() {
        contextRunner.withPropertyValues("spring.profiles.active=local").run { context ->
            assertThat(context.dataLocations()).containsExactly("classpath:seed.sql", "classpath:data-local.sql")
        }
    }

    @Test
    fun `test 프로파일은 사람용 로컬 데이터를 제외한다`() {
        contextRunner.withPropertyValues("spring.profiles.active=test").run { context ->
            assertThat(context.dataLocations()).containsExactly("classpath:seed.sql")
        }
    }

    private fun org.springframework.context.ApplicationContext.dataLocations(): List<String> {
        val locations = Binder.get(environment)
            .bind("spring.sql.init.data-locations", Bindable.listOf(String::class.java))
            .orElse(emptyList())
        return requireNotNull(locations)
    }

    @Configuration(proxyBeanMethods = false)
    class TestConfiguration
}
