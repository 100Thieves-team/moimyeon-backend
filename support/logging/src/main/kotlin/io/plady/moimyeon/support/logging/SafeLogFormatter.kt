package io.plady.moimyeon.support.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.structured.StructuredLogFormatter
import org.springframework.core.env.Environment

class SafeLogFormatter(environment: Environment) : StructuredLogFormatter<ILoggingEvent> {
    private val sanitizer = LogSanitizer(
        properties = LoggingProperties.from(environment),
        service = environment.getProperty("OTEL_SERVICE_NAME") ?: environment.getProperty("spring.application.name"),
        deployment = environment.getProperty("moimyeon.logging.environment"),
        release = environment.getProperty("APP_RELEASE"),
    )
    private val writer = JsonWriter.standard<Map<String, Any>>()

    override fun format(event: ILoggingEvent): String {
        return writer.writeToString(sanitizer.sanitize(event)) + "\n"
    }

    fun formatText(event: ILoggingEvent): String {
        val fields = sanitizer.sanitize(event)
        val details = fields.filterKeys { it !in TEXT_METADATA }.entries.joinToString(" ") { "${it.key}=${it.value}" }
        val eventCode = fields["eventCode"]?.takeIf { it !in TEXT_DEFAULT_EVENTS }
        val header = "${fields["timestamp"] ?: "-"} ${fields["level"]} [${fields["service"]}/${fields["environment"]}] " +
            "${fields["logger"] ?: "logging"}" + (if (eventCode == null) "" else " $eventCode") +
            " - ${fields["message"] ?: ""}" + (if (details.isEmpty()) "" else " | $details")
        val exceptions = (fields["exceptions"] as? List<*>).orEmpty().joinToString("") { exception ->
            val item = exception as? Map<*, *> ?: return@joinToString ""
            val frames = (item["frames"] as? List<*>).orEmpty().joinToString("") { frame ->
                val f = frame as? Map<*, *> ?: return@joinToString ""
                "\n    at ${f["class"]}.${f["method"]}(${f["file"]}:${f["line"]})"
            }
            val message = item["message"]?.toString()?.takeIf { it.isNotEmpty() }
            "\n  ! ${item["type"]}" + (if (message == null) "" else ": $message") + frames
        }
        return "$header$exceptions\n"
    }

    companion object {
        private val TEXT_METADATA = setOf("schemaVersion", "timestamp", "level", "service", "environment", "release", "logger", "thread", "eventCode", "message", "exceptions")
        private val TEXT_DEFAULT_EVENTS = setOf("application.log", "application.error")
    }
}
