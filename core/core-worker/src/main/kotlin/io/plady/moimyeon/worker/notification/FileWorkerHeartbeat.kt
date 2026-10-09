package io.plady.moimyeon.worker.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

private val log = KotlinLogging.logger {}

/**
 * 하트비트 파일에 마지막 소비 주기 완료 시각을 쓴다. 컨테이너 상태 검사는 파일 수정 시각만 본다.
 * 쓰기 실패는 소비를 멈출 이유가 아니므로 경고만 남긴다. 계속 실패하면 상태 검사가 Worker를 교체한다.
 */
class FileWorkerHeartbeat(
    private val file: Path,
    private val clock: Clock,
) : WorkerHeartbeat {
    override fun beat() {
        try {
            Files.writeString(file, clock.instant().toString())
        } catch (exception: IOException) {
            log.warn(exception) { "notification.worker.heartbeat.write_failed file=$file" }
        }
    }
}
