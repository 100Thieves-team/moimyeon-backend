package io.plady.moimyeon.support.monitoring

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.registry.otlp.AggregationTemporality
import io.micrometer.registry.otlp.OtlpConfig
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.micrometer.registry.otlp.OtlpMetricsSender
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.io.ClassPathResource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class MonitoringAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withInitializer { context ->
            YamlPropertySourceLoader().load("monitoring", ClassPathResource("monitoring.yml"))
                .forEach { context.environment.propertySources.addLast(it) }
        }
        .withPropertyValues(
            "spring.application.name=core-worker",
            "spring.profiles.active=dev",
            "SERVICE_INSTANCE_ID=test-instance",
            "APP_RELEASE=test-release",
        )
        .withConfiguration(
            AutoConfigurations.of(
                MetricsAutoConfiguration::class.java,
                OtlpMetricsExportAutoConfiguration::class.java,
                MonitoringAutoConfiguration::class.java,
            ),
        )

    @Test
    fun `기본 설정은 외부 메트릭 전송과 트레이스를 끄고 health만 노출한다`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(OtlpMeterRegistry::class.java)
            assertThat(context.environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health")
            assertThat(context.environment.getProperty("management.tracing.export.otlp.enabled"))
                .isEqualTo("false")
            assertThat(context.environment.getProperty("management.logging.export.otlp.enabled"))
                .isEqualTo("false")
        }
    }

    @Test
    fun `OTLP는 누적 초 단위 히스토그램과 서비스 식별자를 전송한다`() {
        val requests = mutableListOf<OtlpMetricsSender.Request>()
        runner.withPropertyValues(
            "MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=true",
            "MANAGEMENT_OTLP_METRICS_EXPORT_URL=http://collector.internal:4318/v1/metrics",
        )
            .withBean(OtlpMetricsSender::class.java, { OtlpMetricsSender { requests.add(it) } })
            .run { context ->
                assertThat(context).hasNotFailed()
                val config = context.getBean(OtlpConfig::class.java)
                assertThat(config.aggregationTemporality()).isEqualTo(AggregationTemporality.CUMULATIVE)
                assertThat(config.baseTimeUnit()).isEqualTo(TimeUnit.SECONDS)
                assertThat(config.step()).isEqualTo(Duration.ofSeconds(15))
                assertThat(config.resourceAttributes()).containsAllEntriesOf(
                    mapOf(
                        "service.name" to "core-worker",
                        "service.instance.id" to "test-instance",
                        "deployment.environment.name" to "dev",
                        "service.version" to "test-release",
                    ),
                )
                val registry = context.getBean(OtlpMeterRegistry::class.java)
                Timer.builder("http.server.requests")
                    .tag("uri", "/v1/rooms/{id}")
                    .register(registry)
                    .record(Duration.ofMillis(250))
                registry.close()

                assertThat(requests).isNotEmpty()
                assertThat(requests.map { it.address }).containsOnly("http://collector.internal:4318/v1/metrics")
                val metrics = requests.flatMap { request ->
                    ExportMetricsServiceRequest.parseFrom(request.metricsData).resourceMetricsList
                        .flatMap { it.scopeMetricsList }.flatMap { it.metricsList }
                }
                val http = metrics.first { it.name == "http.server.requests" }
                assertThat(http.unit).isEqualTo("seconds")
                assertThat(http.hasHistogram()).isTrue()
                assertThat(http.histogram.dataPointsList.first().sum).isEqualTo(0.25)
                assertThat(http.histogram.dataPointsList.first().explicitBoundsList).contains(0.1, 0.3, 1.0)
                assertThat(metrics.map { it.name }).contains("observability.heartbeat")
            }
    }

    @Test
    fun `heartbeat는 수집 시각의 Unix 초를 제공하며 업무 성공을 의미하지 않는다`() {
        val registry = SimpleMeterRegistry()
        try {
            val time = Instant.parse("2026-09-11T00:00:00Z")
            MonitoringAutoConfiguration().heartbeat(Clock.fixed(time, ZoneOffset.UTC)).bindTo(registry)

            val gauge = registry.get("observability.heartbeat").gauge()
            assertThat(gauge.value()).isEqualTo(time.epochSecond.toDouble())
            assertThat(gauge.id.baseUnit).isEqualTo("seconds")
        } finally {
            registry.close()
        }
    }
}
