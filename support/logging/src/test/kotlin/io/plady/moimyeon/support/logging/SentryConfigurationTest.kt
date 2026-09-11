package io.plady.moimyeon.support.logging

import io.mockk.every
import io.mockk.mockk
import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryItemType
import io.sentry.SentryOptions
import io.sentry.spring.boot4.SentryAutoConfiguration
import io.sentry.spring.boot4.SentryProperties
import io.sentry.transport.ITransport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.event.Level
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.io.ClassPathResource

class SentryConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withInitializer { context ->
            YamlPropertySourceLoader().load("logging", ClassPathResource("logging.yml"))
                .forEach { context.environment.propertySources.addLast(it) }
        }
        .withPropertyValues("spring.application.name=core-worker", "spring.profiles.active=dev")
        .withConfiguration(
            AutoConfigurations.of(SentryAutoConfiguration::class.java, SentryPrivacyAutoConfiguration::class.java),
        )

    @AfterEach
    fun closeSentry() {
        Sentry.close()
    }

    @Test
    fun `DSN이 없는 기본 환경은 전송하지 않고 Boot4 자동 설정을 안전하게 시작한다`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(Sentry.isEnabled()).isFalse()
            val options = context.getBean(SentryProperties::class.java)
            assertThat(options.isEnabled).isFalse()
            assertThat(options.dsn).isEmpty()
            assertThat(options.logs.isEnabled).isFalse()
            assertThat(options.logging.isEnabled).isFalse()
            assertThat(options.logging.minimumEventLevel).isEqualTo(Level.ERROR)
            assertThat(options.isSendDefaultPii).isFalse()
            assertThat(options.isEnableDeduplication).isTrue()
            assertThat(options.tracesSampleRate).isZero()
            assertThat(options.profilesSampleRate).isZero()
            assertThat(options.maxRequestBodySize).isEqualTo(SentryOptions.RequestSize.NONE)
            assertThat(options.beforeSend).isNotNull()
            assertThat(options.beforeBreadcrumb).isNotNull()
            assertThat(options.logs.beforeSend).isNotNull()
        }
    }

    @Test
    fun `배포 환경의 서비스 이름 환경과 릴리스는 오류 이벤트에도 일치한다`() {
        runner.withPropertyValues(
            "OTEL_SERVICE_NAME=core-api",
            "DEPLOYMENT_ENVIRONMENT=staging",
            "APP_RELEASE=exact-source-sha",
        ).run { context ->
            val filter = context.getBean(SentryPrivacyFilter::class.java)
            val event = filter.event(SentryEvent())
            val options = context.getBean(SentryProperties::class.java)

            assertThat(event.getTag("service.name")).isEqualTo("core-api")
            assertThat(event.environment).isEqualTo("staging")
            assertThat(event.release).isEqualTo("exact-source-sha")
            assertThat(options.environment).isEqualTo(event.environment)
            assertThat(options.release).isEqualTo(event.release)
        }
    }

    @Test
    fun `동일 예외를 중복 수집하지 않고 실제 전송 직전에도 민감 정보가 제거된다`() {
        val envelopes = mutableListOf<SentryEnvelope>()
        val transport = mockk<ITransport>(relaxed = true)
        every { transport.rateLimiter } returns null
        every { transport.send(any(), any()) } answers { envelopes.add(firstArg()) }
        runner.withPropertyValues(
            "SENTRY_ENABLED=true",
            // Syntactically valid, non-routable test DSN; transport is replaced below.
            "SENTRY_DSN=https://public@example.invalid/1",
        )
            .withBean(ITransportFactory::class.java, { ITransportFactory { _, _ -> transport } })
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(Sentry.isEnabled()).isTrue()
                val error = IllegalStateException("email=private@example.com token=secret")

                Sentry.captureException(error)
                Sentry.captureException(error)

                val events = envelopes.flatMap { it.items }.filter { it.header.type == SentryItemType.Event }
                assertThat(events).hasSize(1)
                val payload = events.single().data.toString(Charsets.UTF_8)
                assertThat(payload).contains("IllegalStateException", "SentryConfigurationTest")
                assertThat(payload).doesNotContain("private@example.com", "token=secret")
            }
    }
}
