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
        return "${fields["timestamp"] ?: "-"} ${fields["level"]} [${fields["service"]}/${fields["environment"]}] " +
            "${fields["logger"] ?: "logging"} ${fields["eventCode"]}" +
            (if (details.isEmpty()) "" else " $details") + "\n"
    }

    companion object {
        private val TEXT_METADATA = setOf("schemaVersion", "timestamp", "level", "service", "environment", "release", "logger", "eventCode")
    }
}
