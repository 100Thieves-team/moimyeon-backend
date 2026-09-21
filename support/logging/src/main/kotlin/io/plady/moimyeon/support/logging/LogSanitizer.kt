package io.plady.moimyeon.support.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.ThrowableProxy
import java.time.Instant

// Logback 이벤트를 출력용 필드로 바꾼다. 메시지·key-value·MDC·예외 메시지를 보존하고,
// 자격 증명 형태만 LogMasker로 가리며 길이와 예외 깊이에 상한을 둔다.
// 라우터가 읽는 필드는 예약해 MDC·key-value로 주입할 수 없고, 예외 메시지는 정적 문구를
// 선언한 SafeLogMessage 타입에서만 남긴다. 개행·제어 문자는 이스케이프해 텍스트 로그 위조를 막는다.
class LogSanitizer(
    private val properties: LoggingProperties,
    service: String?,
    deployment: String?,
    release: String?,
) {
    private val service = identifier(service) ?: "unknown-service"
    private val deployment = identifier(deployment) ?: "unknown"
    private val release = identifier(release) ?: "local"

    fun sanitize(event: ILoggingEvent): Map<String, Any> = try {
        fields(event)
    } catch (_: Exception) {
        mapOf("level" to "ERROR", "eventCode" to "logging.serialization_failed", "service" to service, "environment" to deployment)
    }

    private fun fields(event: ILoggingEvent): Map<String, Any> {
        val timestamp = Instant.ofEpochMilli(event.timeStamp).toString()
        val request = if (event.message == RequestLogEntry.COMPLETED || event.message == RequestLogEntry.SLOW) {
            event.keyValuePairs?.singleOrNull { it.key == RequestLogEntry.PAYLOAD_KEY }?.value as? RequestLogEntry
        } else {
            null
        }
        val eventCode = when {
            request != null -> event.message
            event.message == SentryPrivacyFilter.SERVICE_READY -> SentryPrivacyFilter.SERVICE_READY
            event.level.isGreaterOrEqual(Level.ERROR) -> "application.error"
            else -> "application.log"
        }
        val fields = linkedMapOf<String, Any>(
            "schemaVersion" to 1,
            "timestamp" to timestamp,
            "service" to service,
            "environment" to deployment,
            "release" to release,
            "level" to event.level.toString(),
            "logger" to text(event.loggerName, MAX_IDENTIFIER_LENGTH, "unknown-logger"),
            "thread" to text(event.threadName, MAX_IDENTIFIER_LENGTH, "unknown-thread"),
            "eventCode" to eventCode,
            "message" to text(event.formattedMessage, MAX_MESSAGE_LENGTH, ""),
        )
        request?.let { fields.putAll(it.fields()) }
        val context = event.mdcPropertyMap.orEmpty()
        if (!fields.containsKey("requestId")) {
            context["requestId"]?.takeIf(RequestLogEntry::isRequestId)?.let { fields["requestId"] = it }
        }
        val traceId = context["traceId"]?.takeIf { TRACE_ID.matches(it) && it.any { character -> character != '0' } }
        if (traceId != null) {
            fields["traceId"] = traceId
            context["spanId"]?.takeIf { SPAN_ID.matches(it) && it.any { character -> character != '0' } }
                ?.let { fields["spanId"] = it }
        }
        event.keyValuePairs.orEmpty()
            .filter { request == null || it.key != RequestLogEntry.PAYLOAD_KEY }
            .forEach { pair -> put(fields, pair.key, pair.value) }
        context.forEach { (key, value) -> put(fields, key, value) }
        event.throwableProxy?.let { fields["exceptions"] = exceptions(it) }
        return fields
    }

    private fun put(fields: MutableMap<String, Any>, key: String?, value: Any?) {
        val name = key?.takeIf { it.isNotBlank() && it.length <= MAX_IDENTIFIER_LENGTH } ?: return
        if (name in RESERVED_FIELDS || name in fields) return
        fields[name] = text(value?.toString(), MAX_VALUE_LENGTH, "")
    }

    private fun exceptions(source: IThrowableProxy): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        var current: IThrowableProxy? = source
        var remainingFrames = properties.maxStackFrames
        while (current != null && result.size < properties.maxExceptionDepth) {
            val frames = current.stackTraceElementProxyArray.orEmpty().take(remainingFrames).map { proxy ->
                val frame = proxy.stackTraceElement
                mapOf(
                    "class" to text(frame.className, MAX_IDENTIFIER_LENGTH, "unknown"),
                    "method" to text(frame.methodName, MAX_IDENTIFIER_LENGTH, "unknown"),
                    "file" to text(frame.fileName, MAX_IDENTIFIER_LENGTH, "unknown"),
                    "line" to frame.lineNumber,
                )
            }
            val type = text(current.className, MAX_IDENTIFIER_LENGTH, "unknown")
            val item = linkedMapOf<String, Any>("type" to type)
            // 메시지는 SafeLogMessage를 구현한 예외에서만 남긴다. 프레임워크·드라이버 예외의 메시지는
            // 거부된 입력값이나 "Duplicate entry '<값>'"처럼 사용자 데이터를 되풀이한다.
            if ((current as? ThrowableProxy)?.throwable is SafeLogMessage) item["message"] = text(current.message, MAX_MESSAGE_LENGTH, "")
            item["frames"] = frames
            result.add(item)
            remainingFrames -= frames.size
            current = current.cause
        }
        return result
    }

    // 개행·캐리지리턴·탭은 이스케이프하고 나머지 제어 문자는 제거한다. 텍스트 로그 한 줄 = 이벤트 하나를 보장한다.
    private fun text(value: String?, limit: Int, fallback: String): String {
        val masked = LogMasker.mask(value ?: return fallback)
        val escaped = buildString(masked.length) {
            for (character in masked) {
                when {
                    character == '\n' -> append("\\n")
                    character == '\r' -> append("\\r")
                    character == '\t' -> append("\\t")
                    character.isISOControl() -> Unit
                    else -> append(character)
                }
            }
        }
        return if (escaped.length <= limit) escaped else escaped.take(limit) + "…"
    }

    private fun identifier(value: String?): String? = value?.takeIf { it.length <= MAX_IDENTIFIER_LENGTH && IDENTIFIER.matches(it) }

    companion object {
        private const val MAX_IDENTIFIER_LENGTH = 256
        private const val MAX_VALUE_LENGTH = 1024
        private const val MAX_MESSAGE_LENGTH = 4096
        private val IDENTIFIER = Regex("[A-Za-z0-9_.$<>:/-]+")

        private val TRACE_ID = Regex("[0-9a-f]{32}")
        private val SPAN_ID = Regex("[0-9a-f]{16}")

        // 출력 스키마와 Fluent Bit 라우터(router/v1/sanitize.lua)가 읽는 필드. MDC·key-value로 덮어쓰지 못한다.
        internal val RESERVED_FIELDS = setOf(
            "schemaVersion", "timestamp", "service", "environment", "release", "level", "logger", "thread", "eventCode", "message",
            "method", "route", "status", "durationMs", "errorCode", "requestId", "traceId", "spanId", "exceptions", "category", "impact",
        )
    }
}
