package io.plady.moimyeon.worker.notification

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FileWorkerHeartbeatTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `하트비트 파일에 소비 주기 완료 시각을 쓴다`() {
        val file = directory.resolve("heartbeat")
        val clock = Clock.fixed(Instant.parse("2026-10-10T01:02:03Z"), ZoneOffset.UTC)

        FileWorkerHeartbeat(file, clock).beat()

        assertThat(Files.readString(file)).isEqualTo("2026-10-10T01:02:03Z")
    }

    @Test
    fun `파일을 쓸 수 없어도 소비 주기를 멈추지 않는다`() {
        val file = directory.resolve("missing-directory").resolve("heartbeat")

        assertThatCode { FileWorkerHeartbeat(file, Clock.systemUTC()).beat() }.doesNotThrowAnyException()
        assertThat(file).doesNotExist()
    }
}
