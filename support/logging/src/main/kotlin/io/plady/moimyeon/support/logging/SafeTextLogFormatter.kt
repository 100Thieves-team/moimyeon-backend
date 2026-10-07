package io.plady.moimyeon.support.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import org.springframework.boot.logging.structured.StructuredLogFormatter
import org.springframework.core.env.Environment

class SafeTextLogFormatter(environment: Environment) : StructuredLogFormatter<ILoggingEvent> {
    private val formatter = SafeLogFormatter(environment)

    override fun format(event: ILoggingEvent): String = formatter.formatText(event)
}
