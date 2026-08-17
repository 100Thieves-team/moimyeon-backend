package io.plady.moimyeon.core.api.auth

import io.mockk.mockk
import io.plady.moimyeon.core.api.controller.v1.DevAuthController
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.JwtTokenProvider
import io.plady.moimyeon.security.auth.SessionIssuer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@Tag("context")
class DevAuthProfileContextTest {
    private val contextRunner = ApplicationContextRunner()
        .withBean(MemberFinder::class.java, { mockk() })
        .withBean(JwtTokenProvider::class.java, { mockk() })
        .withBean(SessionIssuer::class.java, { mockk() })
        .withBean(AuthCookieFactory::class.java, { mockk() })
        .withUserConfiguration(DevSessionCookieIssuer::class.java, DevAuthController::class.java)

    @Test
    fun `local과 local-dev와 dev 프로파일에서 개발 인증 엔드포인트를 등록한다`() {
        listOf("local", "local-dev", "dev").forEach { profile ->
            contextRunner.withPropertyValues("spring.profiles.active=$profile").run { context ->
                assertThat(context).hasSingleBean(DevSessionCookieIssuer::class.java)
                assertThat(context).hasSingleBean(DevAuthController::class.java)
            }
        }
    }

    @Test
    fun `live 프로파일이 함께 활성화되면 개발 인증 엔드포인트를 등록하지 않는다`() {
        contextRunner.withPropertyValues("spring.profiles.active=dev,live").run { context ->
            assertThat(context).doesNotHaveBean(DevSessionCookieIssuer::class.java)
            assertThat(context).doesNotHaveBean(DevAuthController::class.java)
        }
    }

    @Test
    fun `개발용이 아닌 프로파일에는 개발 인증 엔드포인트를 등록하지 않는다`() {
        listOf("staging", "live").forEach { profile ->
            contextRunner.withPropertyValues("spring.profiles.active=$profile").run { context ->
                assertThat(context).doesNotHaveBean(DevSessionCookieIssuer::class.java)
                assertThat(context).doesNotHaveBean(DevAuthController::class.java)
            }
        }
    }
}
