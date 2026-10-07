package io.plady.moimyeon.core.qa

import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Profiles
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.stereotype.Component

class QaPackageProfileGateTest {
    @Test
    fun `core qa 패키지의 모든 스테레오타입 빈은 dev 프로파일 게이트를 가진다`() {
        val scanner = ClassPathScanningCandidateComponentProvider(false).apply {
            environment = AnyProfileEnvironment()
            addIncludeFilter(AnnotationTypeFilter(Component::class.java))
        }
        val beanClasses = scanner.findCandidateComponents(QA_PACKAGE).map { Class.forName(it.beanClassName) }

        assertThat(beanClasses).isNotEmpty
        beanClasses.forEach { beanClass ->
            val profile = beanClass.getAnnotation(Profile::class.java)
            assertThat(profile).describedAs("${beanClass.simpleName} 에 @Profile 이 없다").isNotNull
            assertThat(profile.value).describedAs("${beanClass.simpleName} 의 게이트가 dev-sessions 와 다르다")
                .containsExactly(DEV_AUTH_PROFILE_EXPRESSION)
        }
    }

    private class AnyProfileEnvironment : StandardEnvironment() {
        override fun acceptsProfiles(profiles: Profiles): Boolean = true

        override fun matchesProfiles(vararg profileExpressions: String): Boolean = true
    }

    companion object {
        private const val QA_PACKAGE = "io.plady.moimyeon.core.qa"
    }
}
