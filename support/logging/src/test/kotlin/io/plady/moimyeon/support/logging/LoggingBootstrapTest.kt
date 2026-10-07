package io.plady.moimyeon.support.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.json.JsonParserFactory
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Tag("context")
class LoggingBootstrapTest {
    @Test
    fun `새 JVM의 첫 부팅이 환경 충돌로 실패해도 JSON 출력 설정을 사용한다`() {
        val process = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            System.getProperty("logging.test.classpath"),
            LoggingBootstrapProbe::class.java.name,
        ).redirectErrorStream(true).start()
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue()
            val output = process.inputStream.bufferedReader().readText()
            assertThat(process.exitValue()).isEqualTo(2)
            val events = output.lineSequence().filter { it.startsWith('{') }
                .map { JsonParserFactory.getJsonParser().parseMap(it) }.toList()
            assertThat(events).isNotEmpty()
            assertThat(events.last()).containsEntry("environment", "invalid").containsEntry("level", "ERROR")
                .containsEntry("message", "bootstrap.probe.failed")
        } finally {
            process.destroyForcibly()
        }
    }
}
