package io.plady.moimyeon.support.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import io.sentry.Sentry
import io.sentry.spring.boot4.SentryAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.ResourceLock
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.json.JsonParserFactory
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Tag("context")
@ResourceLock("logback")
@ExtendWith(OutputCaptureExtension::class)
class LoggingConfigurationTest {
    @Test
    fun `설정 객체는 실제 Boot 바인딩으로 시간을 읽고 로그 작성기에 제공한다`() {
        start("dev", "--moimyeon.logging.policy.slow-request-threshold=250ms", "--moimyeon.logging.policy.max-stack-frames=2").use { context ->
            val properties = context.getBean(LoggingProperties::class.java)
            assertThat(properties.slowRequestThreshold).isEqualTo(Duration.ofMillis(250))
            assertThat(properties.maxStackFrames).isEqualTo(2)
            assertThat(context.getBean(RequestLogWriter::class.java)).isNotNull()
        }
    }

    @Test
    fun `잘못된 설정은 안전한 로그 초기화 뒤 시작을 거부한다`(output: CapturedOutput) {
        for (argument in listOf("max-stack-frames=31", "max-exception-depth=0", "slow-request-threshold=-1s", "slow-request-threshold=private@example.invalid", "excluded-paths[0]=private@example.invalid")) {
            assertThatThrownBy { start("dev", "--moimyeon.logging.policy.$argument").close() }
                .hasMessageContaining("Invalid logging properties")
        }
        assertThat(output.all).doesNotContain("private@example.invalid")
    }

    @Test
    fun `환경별 XML과 dev perf 전용 XML을 선택해 JSON 로그를 출력한다`(output: CapturedOutput) {
        for ((profiles, loggingProfile) in listOf("dev" to "dev", "staging" to "staging", "dev,perf" to "dev-perf", "perf,dev" to "dev-perf", "live" to "live")) {
            start(profiles).use { context ->
                assertThat(context.environment.getProperty("logging.config"))
                    .isEqualTo("classpath:logback/logback-$loggingProfile.xml")
                KotlinLogging.logger("io.plady.moimyeon.configuration.probe").info { "service.ready" }

                val event = lastJsonEvent(output)
                assertThat(event).containsEntry("eventCode", "service.ready")
                    .containsEntry("environment", if (loggingProfile == "dev-perf") "dev" else loggingProfile)
                    .containsEntry("service", "logging-test")
                    .containsEntry("release", "test-release")
            }
        }
    }

    @Test
    fun `기본 환경이 충돌하거나 배포 환경 태그가 다르면 시작하지 않는다`() {
        for (profiles in listOf("dev,live", "local,staging", "test,live")) {
            assertThatThrownBy { start(profiles).close() }
                .hasMessageContaining("Conflicting logging environments")
        }
        assertThatThrownBy { start("live", "--DEPLOYMENT_ENVIRONMENT=dev").close() }
            .hasMessageContaining("Deployment environment does not match")
    }

    @Test
    fun `local test와 local dev는 외부 전송 요청을 덮어쓰고 SQL 직접 출력을 차단한다`() {
        for (profiles in listOf("local", "test", "test,local", "test,perf", "local-dev")) {
            start(
                profiles,
                "--spring.profiles.group.test=local",
                "--sentry.enabled=true",
                "--sentry.logging.enabled=true",
                "--sentry.logs.enabled=true",
                "--management.otlp.metrics.export.enabled=true",
                "--spring.jpa.properties.hibernate.show_sql=true",
            ).use { context ->
                val environment = context.environment
                assertThat(environment.getProperty("sentry.enabled", Boolean::class.java)).isFalse()
                assertThat(environment.getProperty("sentry.logging.enabled", Boolean::class.java)).isFalse()
                assertThat(environment.getProperty("sentry.logs.enabled", Boolean::class.java)).isFalse()
                assertThat(environment.getProperty("management.otlp.metrics.export.enabled", Boolean::class.java)).isFalse()
                assertThat(environment.getProperty("spring.jpa.properties.hibernate.show_sql", Boolean::class.java)).isFalse()
                assertThat(environment.getProperty("moimyeon.logging.environment"))
                    .isEqualTo(if (profiles.startsWith("test")) "test" else profiles)
                assertThat(environment.getProperty("logging.config"))
                    .isEqualTo("classpath:logback/logback-${if (profiles.startsWith("test")) "test" else profiles}.xml")
            }
        }
    }

    @Test
    fun `배포 환경의 기존 Sentry 활성화 설정은 유지한다`() {
        start("dev", "--sentry.enabled=true").use { context ->
            assertThat(context.environment.getProperty("sentry.enabled", Boolean::class.java)).isTrue()
        }
    }

    @Test
    fun `상위 logging config 값으로 공통 출력 정책을 우회하지 못한다`(output: CapturedOutput) {
        start("dev", "--logging.config=classpath:does-not-exist.xml").use {
            KotlinLogging.logger("io.plady.moimyeon.configuration.probe").info { "service.ready" }
            assertThat(lastJsonEvent(output)).containsEntry("eventCode", "service.ready")
        }
    }

    @Test
    fun `로컬 부팅에서 실제 Sentry 자동 설정도 외부 전송을 활성화하지 않는다`() {
        try {
            start(
                "local-dev",
                "--sentry.enabled=true",
                "--sentry.logging.enabled=true",
                "--sentry.dsn=https://public@example.invalid/1",
                source = SentryTestApplication::class.java,
            ).use { context ->
                assertThat(context.getBean(SentryPrivacyFilter::class.java)).isNotNull()
                assertThat(Sentry.isEnabled()).isFalse()
            }
        } finally {
            Sentry.close()
        }
    }

    @Test
    fun `로컬 텍스트에도 검증된 trace와 span을 남긴다`(output: CapturedOutput) {
        start("local").use {
            val traceId = "0123456789abcdef0123456789abcdef"
            val spanId = "0123456789abcdef"
            MDC.put("traceId", traceId)
            MDC.put("spanId", spanId)
            try {
                KotlinLogging.logger("io.plady.moimyeon.configuration.probe").info { "service.ready" }
            } finally {
                MDC.clear()
            }
            assertThat(output.out).contains("traceId=$traceId", "spanId=$spanId")
        }
    }

    @Test
    fun `local과 dev의 DEBUG는 켜고 test staging live에서는 기본으로 끈다`() {
        for ((profiles, debugEnabled) in listOf("local" to true, "local-dev" to false, "test" to false, "dev" to true, "staging" to false, "live" to false)) {
            start(profiles).use {
                var evaluated = false
                KotlinLogging.logger("io.plady.moimyeon.configuration.probe").debug {
                    evaluated = true
                    "service.ready"
                }
                assertThat(evaluated).isEqualTo(debugEnabled)
                assertThat(LoggerFactory.getLogger("org.hibernate.SQL").isDebugEnabled).isFalse()
                assertThat(LoggerFactory.getLogger("org.hibernate.orm.jdbc.bind").isTraceEnabled).isFalse()
            }
        }
    }

    @Test
    fun `실제 stdout에 메시지 인자 MDC와 앱 예외 메시지를 보존하고 토큰과 외부 예외 메시지는 가린다`(output: CapturedOutput) {
        start("dev").use {
            val traceId = "0123456789abcdef0123456789abcdef"
            // 시크릿 게이트가 JWT 리터럴을 막으므로 실행 시점에 형태만 조립한다.
            val jwt = listOf("eyJ" + "a".repeat(20), "b".repeat(24), "c".repeat(16)).joinToString(".")
            MDC.put("memberId", "9c1e")
            MDC.put("traceId", traceId)
            try {
                LoggerFactory.getLogger("io.plady.moimyeon.configuration.probe")
                    .atError()
                    .addKeyValue("roomId", "3f2a")
                    .setCause(ProbeException("outer failed $jwt", IllegalArgumentException("inner private@example.invalid")))
                    .log("room.create failed attempt={}", 2)
            } finally {
                MDC.clear()
            }

            val event = lastJsonEvent(output)
            assertThat(event).containsEntry("level", "ERROR")
                .containsEntry("eventCode", "application.error")
                .containsEntry("message", "room.create failed attempt=2")
                .containsEntry("traceId", traceId)
                .containsEntry("memberId", "9c1e")
                .containsEntry("roomId", "3f2a")
            assertThat(event.toString()).contains("ProbeException", "outer failed [MASKED_JWT]", "IllegalArgumentException")
            assertThat(output.all).doesNotContain(jwt).doesNotContain("private@example.invalid")
        }
    }

    private fun lastJsonEvent(output: CapturedOutput): Map<String, Any> {
        val line = output.out.lineSequence().last { it.startsWith('{') }
        return JsonParserFactory.getJsonParser().parseMap(line)
    }

    private fun start(
        profiles: String,
        vararg additionalArguments: String,
        source: Class<*> = LoggingTestApplication::class.java,
    ): ConfigurableApplicationContext = SpringApplication(source).apply {
        setWebApplicationType(WebApplicationType.NONE)
        setBannerMode(Banner.Mode.OFF)
        setLogStartupInfo(false)
        setRegisterShutdownHook(false)
    }.run(
        "--spring.config.location=classpath:logging.yml",
        "--spring.profiles.active=$profiles",
        "--spring.application.name=logging-test",
        "--APP_RELEASE=test-release",
        *additionalArguments,
    )

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(LoggingAutoConfiguration::class)
    class LoggingTestApplication

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(SentryAutoConfiguration::class, SentryPrivacyAutoConfiguration::class)
    class SentryTestApplication
}
